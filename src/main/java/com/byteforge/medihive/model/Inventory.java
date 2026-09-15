package com.byteforge.medihive.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"facility_id", "medicine_id"}))
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) private Facility facility;
    @ManyToOne(optional = false) private Medicine medicine;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private double dailyConsumption;
    @Column(nullable = false) private int reorderLevel;
    @Column(nullable = false) private LocalDate expiryDate;
    @Column(nullable = false) private LocalDateTime lastUpdated;

    protected Inventory() {}
    public Inventory(Facility facility, Medicine medicine, int quantity, double dailyConsumption, int reorderLevel, LocalDate expiryDate) {
        this.facility = facility; this.medicine = medicine; this.quantity = quantity;
        this.dailyConsumption = dailyConsumption; this.reorderLevel = reorderLevel; this.expiryDate = expiryDate;
        this.lastUpdated = LocalDateTime.now();
    }
    @PreUpdate public void touch() { lastUpdated = LocalDateTime.now(); }
    public Long getId() { return id; }
    public Facility getFacility() { return facility; }
    public Medicine getMedicine() { return medicine; }
    public int getQuantity() { return quantity; }
    public double getDailyConsumption() { return dailyConsumption; }
    public int getReorderLevel() { return reorderLevel; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void update(int quantity, double dailyConsumption, int reorderLevel, LocalDate expiryDate) {
        this.quantity = quantity; this.dailyConsumption = dailyConsumption; this.reorderLevel = reorderLevel; this.expiryDate = expiryDate;
    }
    public void addQuantity(int amount) { quantity += amount; }
    public void removeQuantity(int amount) { quantity -= amount; }
}
