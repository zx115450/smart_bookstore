package com.zx.reader.service;

import com.zx.common.exception.ErrorCode;
import com.zx.reader.ReaderException;
import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.CreateNoteRequest;
import com.zx.reader.dto.NoteResponse;
import com.zx.reader.entity.EbookBook;
import com.zx.reader.entity.EbookChapter;
import com.zx.reader.entity.UserNote;
import com.zx.reader.repository.EbookBookRepository;
import com.zx.reader.repository.EbookChapterRepository;
import com.zx.reader.repository.UserNoteRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private UserNoteRepository userNoteRepository;
    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;

    private NoteService service;

    @BeforeEach
    void setUp() {
        ReaderProperties props = new ReaderProperties();
        props.getNotes().setMaxQuoteOnLocked(80);
        service = new NoteService(
                userNoteRepository, ebookBookRepository, ebookChapterRepository,
                new ChapterAccessService(new MediaAccessService(
                        org.mockito.Mockito.mock(com.zx.bookstore.borrow.repository.BorrowOrderRepository.class),
                        org.mockito.Mockito.mock(com.zx.bookstore.trade.repository.TradeOrderRepository.class))),
                props);
    }

    @Test
    void createManualNote_onPreviewChapter() {
        stubBookAndPreviewChapter();
        when(userNoteRepository.save(any())).thenAnswer(inv -> {
            UserNote n = inv.getArgument(0);
            n.setId(9L);
            return n;
        });

        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(101L);
        req.setContent("要点");
        req.setTitle("笔记");

        NoteResponse resp = service.create(1L, req);
        assertThat(resp.getId()).isEqualTo(9L);
        assertThat(resp.getSourceType()).isEqualTo("MANUAL");
    }

    @Test
    void lockedChapter_longQuote_shouldDeny() {
        stubBookAndLockedChapter();
        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(103L);
        req.setContent("笔记");
        req.setQuoteText("x".repeat(100));

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_NOTE_QUOTE_DENIED));
    }

    @Test
    void lockedChapter_longContent_shouldDeny() {
        stubBookAndLockedChapter();
        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(103L);
        req.setContent("x".repeat(2001));

        assertThatThrownBy(() -> service.create(1L, req))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_NOTE_QUOTE_DENIED));
    }

    @Test
    void lockedChapter_mediumContent_shouldAllow() {
        stubBookAndLockedChapter();
        when(userNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(103L);
        req.setContent("x".repeat(100));

        NoteResponse resp = service.create(1L, req);
        assertThat(resp.getContent()).hasSize(100);
    }

    @Test
    void lockedChapter_shortQuote_shouldAllow() {
        stubBookAndLockedChapter();
        when(userNoteRepository.save(any())).thenAnswer(inv -> {
            UserNote n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(103L);
        req.setContent("短评");
        req.setQuoteText("短摘录");

        NoteResponse resp = service.create(1L, req);
        assertThat(resp.getSourceType()).isEqualTo("HIGHLIGHT");
    }

    @Test
    void otherUser_cannotUpdate() {
        UserNote note = new UserNote();
        note.setId(5L);
        note.setUserId(2L);
        note.setContent("secret");
        when(userNoteRepository.findById(5L)).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.get(1L, 5L))
                .isInstanceOf(ReaderException.class)
                .satisfies(ex -> assertThat(((ReaderException) ex).getCode())
                        .isEqualTo(ErrorCode.READER_NOTE_FORBIDDEN));
    }

    @Test
    void deleteOwnedNote() {
        UserNote note = new UserNote();
        note.setId(5L);
        note.setUserId(1L);
        note.setContent("mine");
        when(userNoteRepository.findById(5L)).thenReturn(Optional.of(note));
        when(userNoteRepository.deleteById(5L)).thenReturn(true);

        service.delete(1L, 5L);
        verify(userNoteRepository).deleteById(5L);
    }

    @Test
    void listFiltersByEbook() {
        when(userNoteRepository.listByUserAndEbook(1L, 10L)).thenReturn(List.of());
        assertThat(service.list(1L, 10L)).isEmpty();
        verify(userNoteRepository).listByUserAndEbook(1L, 10L);
    }

    @Test
    void createWithQuoteDefaultsHighlight() {
        stubBookAndPreviewChapter();
        when(userNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateNoteRequest req = new CreateNoteRequest();
        req.setEbookId(10L);
        req.setChapterId(101L);
        req.setContent("注");
        req.setQuoteText("引用");

        service.create(1L, req);
        ArgumentCaptor<UserNote> captor = ArgumentCaptor.forClass(UserNote.class);
        verify(userNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("HIGHLIGHT");
    }

    private void stubBookAndPreviewChapter() {
        EbookBook book = new EbookBook();
        book.setId(10L);
        book.setStatus(1);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        EbookChapter ch = new EbookChapter();
        ch.setId(101L);
        ch.setEbookId(10L);
        ch.setIsPreviewFree(1);
        when(ebookChapterRepository.findById(101L)).thenReturn(Optional.of(ch));
    }

    private void stubBookAndLockedChapter() {
        EbookBook book = new EbookBook();
        book.setId(10L);
        book.setStatus(1);
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(book));
        EbookChapter ch = new EbookChapter();
        ch.setId(103L);
        ch.setEbookId(10L);
        ch.setIsPreviewFree(0);
        when(ebookChapterRepository.findById(103L)).thenReturn(Optional.of(ch));
    }
}
