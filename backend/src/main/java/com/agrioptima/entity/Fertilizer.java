package com.agrioptima.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Fertilizer product. Grade is % N, % P2O5, % K2O by weight. */
@Entity
@Table(name = "fertilizers")
public class Fertilizer extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "n_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal nPct;

    @Column(name = "p2o5_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal p2o5Pct;

    @Column(name = "k2o_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal k2oPct;

    /** INR per kg; indicative, user-editable. */
    @Column(name = "price_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKg;

    @Column(name = "bag_kg", precision = 6, scale = 2)
    private BigDecimal bagKg;

    @Column(name = "source_ref", length = 500)
    private String sourceRef;

    @Column(nullable = false)
    private boolean active = true;

    protected Fertilizer() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getNPct() {
        return nPct;
    }

    public BigDecimal getP2o5Pct() {
        return p2o5Pct;
    }

    public BigDecimal getK2oPct() {
        return k2oPct;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public BigDecimal getBagKg() {
        return bagKg;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public boolean isActive() {
        return active;
    }
}
