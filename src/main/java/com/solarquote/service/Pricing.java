package com.solarquote.service;

import com.solarquote.model.Quotation;
import com.solarquote.model.QuoteItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TreeMap;

/**
 * All quotation arithmetic lives here.
 *
 * <pre>
 *   subtotal  = Σ qty × unit price
 *   discount  = subtotal × discount%          (applied before GST, to every line)
 *   GST       = Σ (line amount − its discount share) × line GST%
 *   grand     = subtotal − discount + GST, rounded to the nearest rupee
 * </pre>
 */
public final class Pricing {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Pricing() {}

    public static void compute(Quotation q) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal gst = BigDecimal.ZERO;
        for (QuoteItem it : q.items) {
            BigDecimal amount = it.amount();
            subtotal = subtotal.add(amount);
            // Summing per-line discounts keeps totals consistent with gstBreakup()
            discount = discount.add(pct(amount, q.discountPct));
            gst = gst.add(pct(taxable(amount, q.discountPct), it.gstRate));
        }
        BigDecimal exact = subtotal.subtract(discount).add(gst);
        BigDecimal rounded = exact.setScale(0, RoundingMode.HALF_UP).setScale(2);

        q.subtotal = subtotal;
        q.discountAmount = discount;
        q.gstTotal = gst;
        q.roundOff = rounded.subtract(exact);
        q.grandTotal = rounded;
        // The subsidy can never exceed what the customer actually pays
        q.subsidy = subsidy(q.systemType, q.capacityKw).min(rounded);
    }

    /** Taxable value and GST amount per GST rate — used for the tax summary on the PDF. */
    public static Map<BigDecimal, BigDecimal[]> gstBreakup(Quotation q) {
        Map<BigDecimal, BigDecimal[]> map = new TreeMap<>();
        for (QuoteItem it : q.items) {
            BigDecimal taxable = taxable(it.amount(), q.discountPct);
            BigDecimal[] row = map.computeIfAbsent(it.gstRate.stripTrailingZeros(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            row[0] = row[0].add(taxable);
            row[1] = row[1].add(pct(taxable, it.gstRate));
        }
        return map;
    }

    /**
     * Indicative PM Surya Ghar central subsidy for residential on-grid systems:
     * rate A per kW for the first 2 kW, rate B per kW for the next 1 kW, capped.
     * Rates are editable in Settings because the scheme changes.
     */
    public static BigDecimal subsidy(String systemType, BigDecimal kw) {
        if (!Settings.getBool("subsidy.enabled") || !"ON_GRID".equals(systemType) || kw == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal two = new BigDecimal("2");
        BigDecimal first = kw.min(two);
        BigDecimal next = kw.subtract(two).max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal amount = first.multiply(Settings.getDecimal("subsidy.rate_upto2"))
                .add(next.multiply(Settings.getDecimal("subsidy.rate_2to3")));
        BigDecimal cap = Settings.getDecimal("subsidy.cap");
        if (cap.signum() > 0) {
            amount = amount.min(cap);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal taxable(BigDecimal amount, BigDecimal discountPct) {
        return amount.subtract(pct(amount, discountPct));
    }

    private static BigDecimal pct(BigDecimal value, BigDecimal percent) {
        return value.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
