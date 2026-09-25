package com.solarquote.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian-style number formatting (1,23,45,678.00) and amount-in-words. */
public final class Money {

    private Money() {}

    /** "1,23,456.00" */
    public static String fmt(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        v = v.setScale(2, RoundingMode.HALF_UP);
        boolean neg = v.signum() < 0;
        String s = v.abs().toPlainString();
        int dot = s.indexOf('.');
        String whole = s.substring(0, dot);
        String frac = s.substring(dot);

        StringBuilder out = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            out.append(whole);
        } else {
            String head = whole.substring(0, len - 3);
            // Group the leading digits in pairs (lakh, crore)
            int first = head.length() % 2;
            if (first > 0) out.append(head, 0, first);
            for (int i = first; i < head.length(); i += 2) {
                if (out.length() > 0) out.append(',');
                out.append(head, i, i + 2);
            }
            out.append(',').append(whole.substring(len - 3));
        }
        return (neg ? "-" : "") + out + frac;
    }

    /** "Rs. 1,23,456.00" — the PDF's built-in fonts have no ₹ glyph. */
    public static String rs(BigDecimal v) {
        return "Rs. " + fmt(v);
    }

    /** "₹ 1,23,456.00" for the Swing UI. */
    public static String inr(BigDecimal v) {
        return "₹ " + fmt(v);
    }

    // ------------------------------------------------------------------
    // Amount in words (Indian numbering)
    // ------------------------------------------------------------------

    private static final String[] ONES = {
        "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
        "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    /** "Rupees One Lakh Twenty Three Thousand Four Hundred Fifty Six Only" */
    public static String words(BigDecimal v) {
        long rupees = v.setScale(0, RoundingMode.HALF_UP).abs().longValue();
        if (rupees == 0) return "Rupees Zero Only";
        StringBuilder sb = new StringBuilder();
        long crore = rupees / 1_00_00_000;
        rupees %= 1_00_00_000;
        long lakh = rupees / 1_00_000;
        rupees %= 1_00_000;
        long thousand = rupees / 1000;
        rupees %= 1000;
        long hundred = rupees / 100;
        long rest = rupees % 100;

        if (crore > 0) sb.append(twoOrMore(crore)).append(" Crore ");
        if (lakh > 0) sb.append(below100((int) lakh)).append(" Lakh ");
        if (thousand > 0) sb.append(below100((int) thousand)).append(" Thousand ");
        if (hundred > 0) sb.append(ONES[(int) hundred]).append(" Hundred ");
        if (rest > 0) sb.append(below100((int) rest)).append(' ');
        return "Rupees " + sb.toString().trim() + " Only";
    }

    // Crores can exceed 99, so fall back to recursion for large values
    private static String twoOrMore(long n) {
        if (n < 100) return below100((int) n);
        return words(BigDecimal.valueOf(n)).replace("Rupees ", "").replace(" Only", "");
    }

    private static String below100(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
    }
}
