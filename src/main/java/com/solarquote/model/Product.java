package com.solarquote.model;

import java.math.BigDecimal;

public class Product {

    public static final String[] CATEGORIES = {
        "PANEL", "INVERTER", "BATTERY", "STRUCTURE", "CABLE", "BOS", "SERVICE", "OTHER"
    };

    public Integer id;
    public String name = "";
    public String category = "OTHER";
    public String brand = "";
    public String unit = "Nos";
    public BigDecimal price = BigDecimal.ZERO;
    public BigDecimal gstRate = new BigDecimal("18");
    public Integer wattage;          // panels only; used by "Auto-fill kit"
    public boolean active = true;

    /** Line description used on a quotation, e.g. "Waaree Mono PERC ... 540Wp". */
    public String displayName() {
        return (brand == null || brand.isBlank()) ? name : brand + " " + name;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
