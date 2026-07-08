package com.zx.reservation.enums;

public enum ReservationOrderStatus {
    BOOKED,
    CANCELLED,
    COMPLETED;

    public boolean canTransitTo(ReservationOrderStatus target) {
        if (this == target) {
            return false;
        }
        return switch (this) {
            case BOOKED -> target == CANCELLED || target == COMPLETED;
            case CANCELLED, COMPLETED -> false;
        };
    }
}
