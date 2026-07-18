package com.zx.bookstore.exception;

public class BookstoreException extends RuntimeException {

    private final int code;

    public BookstoreException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BookstoreException bookNotFound() {
        return new BookstoreException(4001, "图书不存在或已下架");
    }

    public static BookstoreException categoryNotFound() {
        return new BookstoreException(4005, "图书分类不存在或已禁用");
    }

    public static BookstoreException bookshelfNotFound() {
        return new BookstoreException(4006, "书架不存在或已禁用");
    }
}
