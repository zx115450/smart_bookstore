package com.zx.bookstore.borrow.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zx.bookstore.borrow.entity.BorrowOrder;
import com.zx.bookstore.borrow.enums.BorrowOrderStatus;
import com.zx.bookstore.borrow.mapper.BorrowOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowOrderRepositoryUnlockTest {

    @Mock
    private BorrowOrderMapper mapper;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BorrowOrder.class);
    }

    @Test
    void unlockBorrowQueriesBorrowedOnly() {
        when(mapper.selectCount(any())).thenReturn(0L);
        BorrowOrderRepository repository = new BorrowOrderRepository(mapper);

        repository.hasUnlockBorrow(7L, 8L);

        Collection<Object> params = captureQueryParams();
        assertThat(params).contains(BorrowOrderStatus.BORROWED.name());
        assertThat(params).doesNotContain(BorrowOrderStatus.OVERDUE.name());
    }

    @Test
    void activeBorrowQueryIncludesOverdue() {
        when(mapper.selectCount(any())).thenReturn(0L);
        BorrowOrderRepository repository = new BorrowOrderRepository(mapper);

        repository.hasActiveBorrow(7L, 8L);

        Collection<Object> params = captureQueryParams();
        assertThat(params).contains(BorrowOrderStatus.BORROWED.name(), BorrowOrderStatus.OVERDUE.name());
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> captureQueryParams() {
        ArgumentCaptor<Wrapper<BorrowOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectCount(captor.capture());
        Wrapper<BorrowOrder> wrapper = captor.getValue();
        assertThat(wrapper).isInstanceOf(AbstractWrapper.class);
        AbstractWrapper<?, ?, ?> aw = (AbstractWrapper<?, ?, ?>) wrapper;
        aw.getSqlSegment();
        return aw.getParamNameValuePairs().values();
    }
}
