package com.byteforge.medihive.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Allocation {
    public enum State { PENDING, APPROVED, DISPATCHED, DELIVERED, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private SupplyRequest request;
    @ManyToOne(optional=false) private Facility source;
    @Column(nullable=false) private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private State state=State.PENDING;
    @Column(nullable=false) private LocalDateTime updatedAt=LocalDateTime.now();
    protected Allocation() {}
    public Allocation(SupplyRequest request, Facility source, int quantity) { this.request=request; this.source=source; this.quantity=quantity; }
    public Long getId(){return id;} public SupplyRequest getRequest(){return request;} public Facility getSource(){return source;}
    public int getQuantity(){return quantity;} public State getState(){return state;} public LocalDateTime getUpdatedAt(){return updatedAt;}
    public void decide(State state, int quantity){this.state=state;this.quantity=quantity;this.updatedAt=LocalDateTime.now();}
}
