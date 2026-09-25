package com.solarquote.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** One line on a quotation. Values are copied from the catalog, so later price changes don't alter old quotes. */
public class QuoteItem {

    public String description = "";
    public String unit = "Nos";
    public BigDecimal qty = BigDecimal.ONE;
    public BigDecimal unitPrice = BigDecimal.ZERO;
    public BigDecimal gstRate = new BigDecimal("18");

    public static QuoteItem of(Product p, BigDecimal qty) {
        QuoteItem it = new QuoteItem();
        it.description = p.displayName();
        it.unit = p.unit;
        it.qty = qty;
        it.unitPrice = p.price;
        it.gstRate = p.gstRate;
        return it;
    }

    /** qty × unit price, before discount and GST. */
    public BigDecimal amount() {
        return qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }
}
