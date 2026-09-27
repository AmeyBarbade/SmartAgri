package com.agrioptima.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One soil test for a field. N/P/K are plant-available amounts in kg/ha. Micronutrients are in ppm (mg/kg). */
@Entity
@Table(name = "soil_records")
public class SoilRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @Column(name = "sample_date", nullable = false)
    private LocalDate sampleDate;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal nitrogen;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal phosphorus;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal potassium;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ph;

    /** Percent. */
    @Column(name = "organic_carbon", precision = 5, scale = 2)
    private BigDecimal organicCarbon;

    /** Percent. */
    @Column(precision = 5, scale = 2)
    private BigDecimal moisture;

    /** Sulfur (S) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal sulfur;

    /** Zinc (Zn) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal zinc;

    /** Iron (Fe) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal iron;

    /** Copper (Cu) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal copper;

    /** Manganese (Mn) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal manganese;

    /** Boron (B) in ppm. */
    @Column(precision = 8, scale = 2)
    private BigDecimal boron;

    /** Electrical Conductivity (EC) in dS/m. */
    @Column(precision = 5, scale = 2)
    private BigDecimal ec;

    @Column(length = 500)
    private String notes;

    protected SoilRecord() {
    }

    public SoilRecord(Field field, LocalDate sampleDate, BigDecimal nitrogen, BigDecimal phosphorus,
                      BigDecimal potassium, BigDecimal ph, BigDecimal organicCarbon, BigDecimal moisture,
                      String notes) {
        this(field, sampleDate, nitrogen, phosphorus, potassium, ph, organicCarbon, moisture,
                null, null, null, null, null, null, null, notes);
    }

    public SoilRecord(Field field, LocalDate sampleDate, BigDecimal nitrogen, BigDecimal phosphorus,
                      BigDecimal potassium, BigDecimal ph, BigDecimal organicCarbon, BigDecimal moisture,
                      BigDecimal sulfur, BigDecimal zinc, BigDecimal iron, BigDecimal copper,
                      BigDecimal manganese, BigDecimal boron, BigDecimal ec, String notes) {
        this.field = field;
        this.sampleDate = sampleDate;
        this.nitrogen = nitrogen;
        this.phosphorus = phosphorus;
        this.potassium = potassium;
        this.ph = ph;
        this.organicCarbon = organicCarbon;
        this.moisture = moisture;
        this.sulfur = sulfur;
        this.zinc = zinc;
        this.iron = iron;
        this.copper = copper;
        this.manganese = manganese;
        this.boron = boron;
        this.ec = ec;
        this.notes = notes;
    }

    public Field getField() {
        return field;
    }

    public LocalDate getSampleDate() {
        return sampleDate;
    }

    public BigDecimal getNitrogen() {
        return nitrogen;
    }

    public BigDecimal getPhosphorus() {
        return phosphorus;
    }

    public BigDecimal getPotassium() {
        return potassium;
    }

    public BigDecimal getPh() {
        return ph;
    }

    public BigDecimal getOrganicCarbon() {
        return organicCarbon;
    }

    public BigDecimal getMoisture() {
        return moisture;
    }

    public BigDecimal getSulfur() {
        return sulfur;
    }

    public BigDecimal getZinc() {
        return zinc;
    }

    public BigDecimal getIron() {
        return iron;
    }

    public BigDecimal getCopper() {
        return copper;
    }

    public BigDecimal getManganese() {
        return manganese;
    }

    public BigDecimal getBoron() {
        return boron;
    }

    public BigDecimal getEc() {
        return ec;
    }

    public String getNotes() {
        return notes;
    }
}
