package com.zx.reader.service;

import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.common.exception.ErrorCode;
import com.zx.media.client.BookstoreMediaProperties;
import com.zx.media.client.LiteMediaClient;
import com.zx.media.client.MockLiteMediaClient;
import com.zx.media.client.dto.MediaInfo;
import com.zx.media.client.dto.PlaySignature;
import com.zx.reader.ReaderException;
import com.zx.reader.dto.BindBookMediaRequest;
import com.zx.reader.dto.BookMediaPlayResponse;
import com.zx.reader.dto.BookMediaRefResponse;
import com.zx.reader.entity.BookMediaRef;
import com.zx.reader.repository.BookMediaRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMediaServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookMediaRefRepository bookMediaRefRepository;
    @Mock
    private LiteMediaClient liteMediaClient;
    @Mock
    private MediaAccessService mediaAccessService;

    private BookstoreMediaProperties mediaProperties;
    private BookMediaService service;

    @BeforeEach
    void setUp() {
        mediaProperties = new BookstoreMediaProperties();
        mediaProperties.setDefaultPreviewSeconds(300);
        service = new BookMediaService(
                bookRepository, bookMediaRefRepository, liteMediaClient, mediaAccessService, mediaProperties);
    }

    @Test
    void bind_shouldPersistFinishedVideo() {
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book(10L)));
        when(liteMediaClient.getMedia(MockLiteMediaClient.VIDEO_FILE_ID)).thenReturn(finishedVideo());
        when(bookMediaRefRepository.findByBookIdAndFileId(10L, MockLiteMediaClient.VIDEO_FILE_ID))
                .thenReturn(Optional.empty());
        when(bookMediaRefRepository.save(any())).thenAnswer(inv -> {
            BookMediaRef ref = inv.getArgument(0);
            ref.setId(7L);
            return ref;
        });

        BindBookMediaRequest req = new BindBookMediaRequest();
        req.setFileId(MockLiteMediaClient.VIDEO_FILE_ID);
        req.setTitle("导读");

        BookMediaRefResponse resp = service.bind(10L, req);

        assertThat(resp.getId()).isEqualTo(7L);
        assertThat(resp.getFileId()).isEqualTo(MockLiteMediaClient.VIDEO_FILE_ID);
        assertThat(resp.getTitle()).isEqualTo("导读");
        assertThat(resp.getPreviewSeconds()).isEqualTo(300);

        ArgumentCaptor<BookMediaRef> captor = ArgumentCaptor.forClass(BookMediaRef.class);
        verify(bookMediaRefRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaType()).isEqualTo("INTRO");
    }

    @Test
    void bind_shouldRejectNonVideo() {
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book(10L)));
        when(liteMediaClient.getMedia("doc-1")).thenReturn(new MediaInfo(
                "doc-1", "DOCUMENT", "a.md", "text/markdown", null, null, "FINISHED", "已完成", null));

        BindBookMediaRequest req = new BindBookMediaRequest();
        req.setFileId("doc-1");

        assertThatThrownBy(() -> service.bind(10L, req))
                .isInstanceOf(com.zx.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void listUser_shouldHideFileId() {
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book(10L)));
        BookMediaRef ref = activeRef(7L, 10L, MockLiteMediaClient.VIDEO_FILE_ID);
        when(bookMediaRefRepository.listByBookId(10L)).thenReturn(List.of(ref));

        List<BookMediaRefResponse> list = service.listUser(10L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(7L);
        assertThat(list.get(0).getFileId()).isNull();
        assertThat(list.get(0).getTitle()).isEqualTo("导读");
    }

    @Test
    void play_previewWhenNotUnlocked() {
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book(10L)));
        when(bookMediaRefRepository.findActiveByBookIdAndId(10L, 7L))
                .thenReturn(Optional.of(activeRef(7L, 10L, MockLiteMediaClient.VIDEO_FILE_ID)));
        when(mediaAccessService.canWatchFullMedia(eq(1L), eq(10L), any())).thenReturn(false);
        when(liteMediaClient.getPlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, true))
                .thenReturn(new PlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, "http://play?p=1", "s", 999L));

        BookMediaPlayResponse resp = service.play(1L, List.of("USER"), 10L, 7L);

        assertThat(resp.isPreview()).isTrue();
        assertThat(resp.getPreviewSeconds()).isEqualTo(300);
        assertThat(resp.getPlayUrl()).contains("http://play");
        verify(liteMediaClient).getPlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, true);
    }

    @Test
    void play_fullWhenBorrowed() {
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book(10L)));
        when(bookMediaRefRepository.findActiveByBookIdAndId(10L, 7L))
                .thenReturn(Optional.of(activeRef(7L, 10L, MockLiteMediaClient.VIDEO_FILE_ID)));
        when(mediaAccessService.canWatchFullMedia(eq(1L), eq(10L), any())).thenReturn(true);
        when(liteMediaClient.getPlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, false))
                .thenReturn(new PlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, "http://play?full=1", "s", 999L));

        BookMediaPlayResponse resp = service.play(1L, List.of("USER"), 10L, 7L);

        assertThat(resp.isPreview()).isFalse();
        assertThat(resp.getPreviewSeconds()).isNull();
        verify(liteMediaClient).getPlaySignature(MockLiteMediaClient.VIDEO_FILE_ID, false);
    }

    @Test
    void play_deniedWhenPreviewSecondsZeroAndLocked() {
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book(10L)));
        BookMediaRef ref = activeRef(7L, 10L, MockLiteMediaClient.VIDEO_FILE_ID);
        ref.setPreviewSeconds(0);
        when(bookMediaRefRepository.findActiveByBookIdAndId(10L, 7L)).thenReturn(Optional.of(ref));
        when(mediaAccessService.canWatchFullMedia(eq(1L), eq(10L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.play(1L, List.of("USER"), 10L, 7L))
                .isInstanceOf(ReaderException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MEDIA_PLAY_DENIED);
        verify(liteMediaClient, never()).getPlaySignature(any(), any(Boolean.class));
    }

    @Test
    void play_mapsMissingFileToNotReady() {
        when(bookRepository.findEnabledById(10L)).thenReturn(Optional.of(book(10L)));
        when(bookMediaRefRepository.findActiveByBookIdAndId(10L, 7L))
                .thenReturn(Optional.of(activeRef(7L, 10L, "gone")));
        when(mediaAccessService.canWatchFullMedia(eq(1L), eq(10L), any())).thenReturn(false);
        when(liteMediaClient.getPlaySignature("gone", true))
                .thenThrow(ReaderException.chapterNotReady("媒资不存在"));

        assertThatThrownBy(() -> service.play(1L, List.of("USER"), 10L, 7L))
                .isInstanceOf(ReaderException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.MEDIA_CHAPTER_NOT_READY);
    }

    private static Book book(Long id) {
        Book book = new Book();
        book.setId(id);
        book.setStatus(1);
        return book;
    }

    private static BookMediaRef activeRef(Long id, Long bookId, String fileId) {
        BookMediaRef ref = new BookMediaRef();
        ref.setId(id);
        ref.setBookId(bookId);
        ref.setFileId(fileId);
        ref.setTitle("导读");
        ref.setMediaType("INTRO");
        ref.setPreviewSeconds(300);
        ref.setSortOrder(0);
        ref.setStatus(1);
        return ref;
    }

    private static MediaInfo finishedVideo() {
        return new MediaInfo(
                MockLiteMediaClient.VIDEO_FILE_ID,
                "VIDEO",
                "intro.mp4",
                "video/mp4",
                null,
                null,
                "FINISHED",
                "已完成",
                null);
    }
}
