package com.byteforge.medihive.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class SupplyRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) private Facility requester;
    @ManyToOne(optional = false) private Medicine medicine;
    @Column(nullable = false) private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status;
    @Column(length = 500) private String notes;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    protected SupplyRequest() {}
    public SupplyRequest(Facility requester, Medicine medicine, int quantity, Priority priority, String notes) {
        this.requester = requester; this.medicine = medicine; this.quantity = quantity;
        this.priority = priority; this.notes = notes; this.status = RequestStatus.REQUESTED;
        this.createdAt = LocalDateTime.now(); this.updatedAt = this.createdAt;
    }
    public Long getId() { return id; }
    public Facility getRequester() { return requester; }
    public Medicine getMedicine() { return medicine; }
    public int getQuantity() { return quantity; }
    public Priority getPriority() { return priority; }
    public RequestStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setStatus(RequestStatus status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
}
