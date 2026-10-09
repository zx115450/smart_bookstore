package com.zx.reader.repository;

import com.zx.reader.entity.EbookChapter;
import com.zx.reader.mapper.EbookChapterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbookChapterRepositoryTest {

    @Mock
    private EbookChapterMapper mapper;

    private EbookChapterRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EbookChapterRepository(mapper);
    }

    @Test
    void insertAll_empty_shouldSkipMapper() {
        assertThat(repository.insertAll(List.of())).isZero();
        verify(mapper, never()).insertBatch(anyList());
    }

    @Test
    void insertAll_shouldChunkByBatchSize() {
        int n = EbookChapterRepository.INSERT_BATCH_SIZE + 3;
        List<EbookChapter> rows = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            EbookChapter c = new EbookChapter();
            c.setEbookId(1L);
            c.setChapterNo(i);
            c.setTitle("c" + i);
            c.setChapterFileId("f" + i);
            c.setWordCount(1);
            rows.add(c);
        }
        when(mapper.insertBatch(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        assertThat(repository.insertAll(rows)).isEqualTo(n);
        verify(mapper, times(2)).insertBatch(anyList());
    }
}
