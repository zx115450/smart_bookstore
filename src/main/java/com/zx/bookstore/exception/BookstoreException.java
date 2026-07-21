package com.zx.bookstore.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

public class BookstoreException extends BusinessException {

    public BookstoreException(int code, String message) {
        super(code, message);
    }

    public static BookstoreException bookNotFound() {
        return new BookstoreException(ErrorCode.BOOKSTORE_BOOK_NOT_FOUND, "图书不存在或已下架");
    }

    public static BookstoreException categoryNotFound() {
        return new BookstoreException(ErrorCode.BOOKSTORE_CATEGORY_NOT_FOUND, "图书分类不存在或已禁用");
    }

    public static BookstoreException bookshelfNotFound() {
        return new BookstoreException(ErrorCode.BOOKSTORE_BOOKSHELF_NOT_FOUND, "书架不存在或已禁用");
    }

    public static BookstoreException outOfStock() {
        return new BookstoreException(ErrorCode.BOOKSTORE_OUT_OF_STOCK, "库存不足");
    }

    public static BookstoreException invalidStatus() {
        return new BookstoreException(ErrorCode.BOOKSTORE_INVALID_STATUS, "状态不允许该操作");
    }

    public static BookstoreException orderNotFound() {
        return new BookstoreException(ErrorCode.BOOKSTORE_ORDER_NOT_FOUND, "订单不存在");
    }

    public static BookstoreException forbidden() {
        return new BookstoreException(ErrorCode.BOOKSTORE_FORBIDDEN, "无权操作");
    }
}
