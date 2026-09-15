package com.byteforge.medihive.service;

import com.byteforge.medihive.model.FacilityType;
import com.byteforge.medihive.model.Inventory;
import java.time.LocalDate;

/** One rule shared by suggestions, offers, approvals and dispatches. */
public final class StockPolicy {
    public static final int HOSPITAL_EXTRA_RESERVE = 50;

    private StockPolicy() {}

    public static int safetyFloor(int reorderLevel, double dailyConsumption) {
        return Math.max(reorderLevel, (int) Math.ceil(dailyConsumption * 5));
    }

    public static int safetyFloor(Inventory item) {
        return safetyFloor(item.getReorderLevel(), item.getDailyConsumption());
    }

    public static int extraReserve(Inventory item) {
        return item.getFacility().getType() == FacilityType.HOSPITAL ? HOSPITAL_EXTRA_RESERVE : 0;
    }

    public static int retainedQuantity(Inventory item) {
        return safetyFloor(item) + extraReserve(item);
    }

    public static int transferable(Inventory item, int approvedReservations) {
        if (item.getExpiryDate().isBefore(LocalDate.now())) return 0;
        return Math.max(0, item.getQuantity() - retainedQuantity(item) - approvedReservations);
    }
}
