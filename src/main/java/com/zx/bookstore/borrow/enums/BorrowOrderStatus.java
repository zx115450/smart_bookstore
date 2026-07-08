package com.zx.bookstore.borrow.enums;

public enum BorrowOrderStatus {
    APPLIED,
    BORROWED,
    RETURNED,
    OVERDUE,
    CANCELLED;

    public boolean canTransitTo(BorrowOrderStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case APPLIED -> target == BORROWED || target == CANCELLED;
            case BORROWED -> target == RETURNED || target == OVERDUE;
            case OVERDUE -> target == RETURNED;
            case RETURNED, CANCELLED -> false;
        };
    }
}
