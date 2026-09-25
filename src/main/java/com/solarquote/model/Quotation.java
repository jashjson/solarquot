package com.solarquote.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Quotation {

    public static final String[] SYSTEM_TYPES = {"ON_GRID", "OFF_GRID", "HYBRID"};
    public static final String[] STATUSES = {"DRAFT", "SENT", "ACCEPTED", "REJECTED"};

    public Integer id;                       // null until saved
    public String quoteNo;                   // assigned on first save
    public LocalDate quoteDate = LocalDate.now();
    public LocalDate validUntil = LocalDate.now().plusDays(30);

    public String customerName = "";
    public String customerPhone = "";
    public String customerEmail = "";
    public String customerAddress = "";

    public String systemType = "ON_GRID";
    public BigDecimal capacityKw = new BigDecimal("3");
    public BigDecimal discountPct = BigDecimal.ZERO;

    // Totals, filled by Pricing.compute()
    public BigDecimal subtotal = BigDecimal.ZERO;
    public BigDecimal discountAmount = BigDecimal.ZERO;
    public BigDecimal gstTotal = BigDecimal.ZERO;
    public BigDecimal roundOff = BigDecimal.ZERO;
    public BigDecimal grandTotal = BigDecimal.ZERO;
    public BigDecimal subsidy = BigDecimal.ZERO;

    public boolean showSubsidy = true;
    public String status = "DRAFT";
    public String terms = "";

    public List<QuoteItem> items = new ArrayList<>();

    public static String systemLabel(String type) {
        return switch (type) {
            case "ON_GRID" -> "On-Grid";
            case "OFF_GRID" -> "Off-Grid";
            case "HYBRID" -> "Hybrid";
            default -> type;
        };
    }
}
