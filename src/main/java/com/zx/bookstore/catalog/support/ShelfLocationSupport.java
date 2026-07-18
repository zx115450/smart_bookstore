package com.zx.bookstore.catalog.support;

import com.zx.bookstore.catalog.entity.Bookshelf;

public final class ShelfLocationSupport {

    private ShelfLocationSupport() {
    }

    public static String format(Integer floor, String code, Integer shelfLayer) {
        if (floor == null || code == null || code.isBlank() || shelfLayer == null) {
            return null;
        }
        return floor + "楼 " + code + " 第" + shelfLayer + "层";
    }

    public static String format(Bookshelf bookshelf, Integer shelfLayer) {
        if (bookshelf == null) {
            return null;
        }
        return format(bookshelf.getFloor(), bookshelf.getCode(), shelfLayer);
    }
}
