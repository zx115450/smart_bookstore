package com.zx.reader.service;

import com.zx.reader.config.ReaderProperties;
import com.zx.reader.dto.NoteResponse;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceAiSaveTest {

    @Mock
    private UserNoteRepository userNoteRepository;
    @Mock
    private EbookBookRepository ebookBookRepository;
    @Mock
    private EbookChapterRepository ebookChapterRepository;

    private NoteService service;

    @BeforeEach
    void setUp() {
        service = new NoteService(
                userNoteRepository, ebookBookRepository, ebookChapterRepository,
                new ChapterAccessService(new MediaAccessService(
                        org.mockito.Mockito.mock(com.zx.bookstore.borrow.repository.BorrowOrderRepository.class),
                        org.mockito.Mockito.mock(com.zx.bookstore.trade.repository.TradeOrderRepository.class))),
                new ReaderProperties());
    }

    @Test
    void saveAiNote_shouldPersistRewriteType() {
        when(ebookBookRepository.findById(10L)).thenReturn(Optional.of(new com.zx.reader.entity.EbookBook()));
        when(userNoteRepository.save(any())).thenAnswer(inv -> {
            UserNote n = inv.getArgument(0);
            n.setId(7L);
            return n;
        });

        NoteResponse resp = service.saveAiNote(1L, 10L, null, "AI_REWRITE", "改写", "短");

        assertThat(resp.getSourceType()).isEqualTo("AI_REWRITE");
        ArgumentCaptor<UserNote> captor = ArgumentCaptor.forClass(UserNote.class);
        verify(userNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("AI_REWRITE");
    }

    @Test
    void saveAiNote_rejectsManualType() {
        assertThatThrownBy(() -> service.saveAiNote(1L, 10L, null, "MANUAL", "t", "c"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
