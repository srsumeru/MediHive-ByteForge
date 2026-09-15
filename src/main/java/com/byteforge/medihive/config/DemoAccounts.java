package com.byteforge.medihive.config;
import java.util.List;
/** Fixed, fictional, local-only hackathon accounts. Replace for a real deployment. */
public final class DemoAccounts {
    private DemoAccounts() {}
    public record Account(String username, String password, String role, String facilityName) {}
    public static final List<Account> ALL = List.of(
        new Account("warehouse_admin", "HiveWH!2026", "WAREHOUSE", "Central Medical Warehouse"),
        new Account("unity_admin", "UnityH!2026", "HOSPITAL", "Unity Hospital"),
        new Account("city_hospital_admin", "CityH!2026", "HOSPITAL", "City Hospital"),
        new Account("citycare_admin", "CityCareP!2026", "PHARMACY", "CityCare Pharmacy"),
        new Account("mediplus_admin", "MediPlusP!2026", "PHARMACY", "MediPlus Pharmacy")
    );
    public static Account find(String username) {
        return ALL.stream().filter(a -> a.username().equals(username)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Unknown demonstration account"));
    }
}
