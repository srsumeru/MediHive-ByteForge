package com.byteforge.medihive.model;

import jakarta.persistence.*;

@Entity
public class Facility {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FacilityType type;
    @Column(nullable = false) private String city;

    protected Facility() {}
    public Facility(String name, FacilityType type, String city) { this.name = name; this.type = type; this.city = city; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public FacilityType getType() { return type; }
    public String getCity() { return city; }
}
