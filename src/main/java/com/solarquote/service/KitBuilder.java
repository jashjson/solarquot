package com.solarquote.service;

import com.solarquote.model.Product;
import com.solarquote.model.QuoteItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Suggests a complete bill of materials for a system size from the product catalog:
 * <ul>
 *   <li>Panels: enough of the first panel (with a wattage) to reach the capacity</li>
 *   <li>Inverter: the smallest suitable one whose name says "N kW" with N ≥ capacity</li>
 *   <li>Battery: only for off-grid / hybrid systems</li>
 *   <li>Structure, cables, BOS, services: products sold per "kW" get qty = capacity, others 1</li>
 * </ul>
 * The user can edit everything afterwards; this just saves typing.
 */
public final class KitBuilder {

    private static final Pattern KW = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*kW\\b", Pattern.CASE_INSENSITIVE);

    private KitBuilder() {}

    public static List<QuoteItem> build(String systemType, BigDecimal kw, List<Product> catalog) {
        List<QuoteItem> items = new ArrayList<>();
        boolean onGrid = "ON_GRID".equals(systemType);

        // Panels
        catalog.stream()
                .filter(p -> "PANEL".equals(p.category) && p.wattage != null && p.wattage > 0)
                .findFirst()
                .ifPresent(p -> {
                    BigDecimal qty = kw.multiply(new BigDecimal(1000))
                            .divide(new BigDecimal(p.wattage), 0, RoundingMode.CEILING);
                    items.add(QuoteItem.of(p, qty));
                });

        // Inverter
        Product inverter = pickInverter(catalog, kw, onGrid);
        if (inverter != null) items.add(QuoteItem.of(inverter, BigDecimal.ONE));

        // Battery
        if (!onGrid) {
            catalog.stream().filter(p -> "BATTERY".equals(p.category)).findFirst()
                    .ifPresent(p -> items.add(QuoteItem.of(p, BigDecimal.ONE)));
        }

        // Everything else needed for installation
        for (String cat : new String[]{"STRUCTURE", "CABLE", "BOS", "SERVICE"}) {
            for (Product p : catalog) {
                if (!cat.equals(p.category)) continue;
                if (!onGrid && p.name.toLowerCase().contains("net meter")) continue;
                BigDecimal qty = "kW".equalsIgnoreCase(p.unit) ? kw : BigDecimal.ONE;
                items.add(QuoteItem.of(p, qty));
            }
        }
        return items;
    }

    private static Product pickInverter(List<Product> catalog, BigDecimal kw, boolean onGrid) {
        Product best = null;
        BigDecimal bestSize = null;
        Product fallback = null;
        for (Product p : catalog) {
            if (!"INVERTER".equals(p.category)) continue;
            String n = p.name.toLowerCase();
            boolean hybridLike = n.contains("hybrid") || n.contains("off-grid") || n.contains("off grid");
            if (onGrid == hybridLike) continue;           // wrong kind of inverter
            if (fallback == null) fallback = p;
            Matcher m = KW.matcher(p.name);
            if (m.find()) {
                BigDecimal size = new BigDecimal(m.group(1));
                if (size.compareTo(kw) >= 0 && (bestSize == null || size.compareTo(bestSize) < 0)) {
                    best = p;
                    bestSize = size;
                }
            }
        }
        return best != null ? best : fallback;
    }
}
