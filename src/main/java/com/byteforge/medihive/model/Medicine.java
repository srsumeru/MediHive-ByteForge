package com.byteforge.medihive.model;

import jakarta.persistence.*;

@Entity
public class Medicine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true) private String name;
    @Column(nullable = false) private String genericName;
    @Column(nullable = false) private String category;
    @Column(nullable = false) private String unit;

    protected Medicine() {}
    public Medicine(String name, String genericName, String category, String unit) {
        this.name = name; this.genericName = genericName; this.category = category; this.unit = unit;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getGenericName() { return genericName; }
    public String getCategory() { return category; }
    public String getUnit() { return unit; }
}
