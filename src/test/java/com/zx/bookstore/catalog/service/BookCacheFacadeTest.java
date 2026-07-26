package com.zx.bookstore.catalog.service;

import com.zx.bookstore.catalog.dto.BookResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookCacheFacadeTest {

    @Mock
    private BookLocalCacheService localCache;
    @Mock
    private BookRedisService redisCache;
    @InjectMocks
    private BookCacheFacade facade;

    @Test
    void put_shouldWriteBothLayers() {
        BookResponse book = sample(1L);
        facade.put(book);
        verify(redisCache).put(book);
        verify(localCache).put(book);
    }

    @Test
    void evict_shouldClearBothLayers() {
        facade.evict(9L);
        verify(redisCache).evict(9L);
        verify(localCache).evict(9L);
    }

    @Test
    void fillLocal_shouldOnlyTouchL1() {
        BookResponse book = sample(2L);
        when(redisCache.get(2L)).thenReturn(Optional.of(book));

        Optional<BookResponse> fromRedis = facade.getRedis(2L);
        assertTrue(fromRedis.isPresent());
        facade.fillLocal(fromRedis.get());

        verify(localCache).put(book);
        assertEquals(2L, fromRedis.get().getId());
    }

    private static BookResponse sample(Long id) {
        BookResponse r = new BookResponse();
        r.setId(id);
        r.setTitle("t");
        r.setPrice(BigDecimal.ONE);
        return r;
    }
}
