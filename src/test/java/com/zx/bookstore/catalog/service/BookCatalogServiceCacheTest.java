package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.BookResponse;
import com.zx.bookstore.catalog.entity.Book;
import com.zx.bookstore.catalog.metrics.BookCacheMetrics;
import com.zx.bookstore.catalog.repository.BookCategoryRepository;
import com.zx.bookstore.catalog.repository.BookRepository;
import com.zx.bookstore.catalog.repository.BookshelfRepository;
import com.zx.reader.repository.EbookBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCatalogServiceCacheTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCategoryRepository categoryRepository;
    @Mock
    private BookshelfRepository bookshelfRepository;
    @Mock
    private BookCacheFacade bookCacheFacade;
    @Mock
    private BookStockLogService bookStockLogService;
    @Mock
    private BookBloomRedisService bookBloomRedisService;
    @Mock
    private BookHotKeyService bookHotKeyService;
    @Mock
    private BookCacheMetrics bookCacheMetrics;
    @Mock
    private EbookBookRepository ebookBookRepository;

    @InjectMocks
    private BookCatalogService catalogService;

    @Test
    void getBookDetail_l1Hit_shouldSkipRedisAndDb() {
        BookResponse cached = sampleResponse(11L);
        when(bookBloomRedisService.mightContain(11L)).thenReturn(true);
        when(bookCacheFacade.getLocal(11L)).thenReturn(Optional.of(cached));

        BookResponse result = catalogService.getBookDetail(11L);

        assertEquals("cached", result.getTitle());
        verify(bookCacheMetrics).onRequest();
        verify(bookCacheMetrics).onL1Hit();
        verify(bookCacheFacade, never()).getRedis(any());
        verify(bookRepository, never()).findEnabledById(any());
        verify(bookHotKeyService).recordAccess(11L);
    }

    @Test
    void getBookDetail_l2Hit_shouldFillLocal() {
        BookResponse cached = sampleResponse(12L);
        when(bookBloomRedisService.mightContain(12L)).thenReturn(true);
        when(bookCacheFacade.getLocal(12L)).thenReturn(Optional.empty());
        when(bookCacheFacade.getRedis(12L)).thenReturn(Optional.of(cached));

        BookResponse result = catalogService.getBookDetail(12L);

        assertEquals(12L, result.getId());
        verify(bookCacheMetrics).onRequest();
        verify(bookCacheMetrics).onL2Hit();
        verify(bookCacheFacade).fillLocal(cached);
        verify(bookRepository, never()).findEnabledById(any());
        verify(bookHotKeyService).recordAccess(12L);
    }

    @Test
    void getBookDetail_miss_shouldLoadDbAndPutBoth() {
        when(bookBloomRedisService.mightContain(13L)).thenReturn(true);
        when(bookCacheFacade.getLocal(13L)).thenReturn(Optional.empty());
        when(bookCacheFacade.getRedis(13L)).thenReturn(Optional.empty());

        Book book = new Book();
        book.setId(13L);
        book.setCategoryId(1L);
        book.setTitle("from-db");
        book.setPrice(BigDecimal.TEN);
        book.setSaleStock(1);
        book.setBorrowStock(1);
        book.setBorrowDays(30);
        book.setStatus(1);
        when(bookRepository.findEnabledById(13L)).thenReturn(Optional.of(book));
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        BookResponse result = catalogService.getBookDetail(13L);

        assertEquals("from-db", result.getTitle());
        verify(bookCacheMetrics).onRequest();
        verify(bookCacheMetrics).onMiss();
        verify(bookCacheFacade).put(any(BookResponse.class));
        verify(bookHotKeyService).recordAccess(13L);
    }

    @Test
    void getBookDetail_bloomReject_shouldNotHitCacheOrDb() {
        when(bookBloomRedisService.mightContain(99L)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.zx.bookstore.exception.BookstoreException.class,
                () -> catalogService.getBookDetail(99L));

        verify(bookCacheMetrics).onRequest();
        verify(bookCacheMetrics).onBloomReject();
        verify(bookCacheFacade, never()).getLocal(any());
        verify(bookCacheFacade, never()).getRedis(any());
        verify(bookRepository, never()).findEnabledById(any());
    }

    @Test
    void getBookDetail_bloomFalsePositive_shouldNotCountAsMiss() {
        when(bookBloomRedisService.mightContain(98L)).thenReturn(true);
        when(bookCacheFacade.getLocal(98L)).thenReturn(Optional.empty());
        when(bookCacheFacade.getRedis(98L)).thenReturn(Optional.empty());
        when(bookRepository.findEnabledById(98L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.zx.bookstore.exception.BookstoreException.class,
                () -> catalogService.getBookDetail(98L));

        verify(bookCacheMetrics).onRequest();
        verify(bookCacheMetrics).onBloomFalsePositive();
        verify(bookCacheMetrics, never()).onMiss();
        verify(bookCacheFacade, never()).put(any());
    }

    private static BookResponse sampleResponse(Long id) {
        BookResponse r = new BookResponse();
        r.setId(id);
        r.setTitle("cached");
        r.setPrice(BigDecimal.ONE);
        return r;
    }
}
