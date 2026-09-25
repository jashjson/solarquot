package com.solarquote.dao;

import com.solarquote.db.Db;
import com.solarquote.model.Quotation;
import com.solarquote.model.QuoteItem;
import com.solarquote.service.Settings;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuotationDao {

    private QuotationDao() {}

    // ------------------------------------------------------------------
    // Save / load
    // ------------------------------------------------------------------

    /** Inserts or updates the quotation and replaces its items, in one transaction. */
    public static void save(Quotation q) throws SQLException {
        boolean isNew = q.id == null;
        try (Connection con = Db.get()) {
            con.setAutoCommit(false);
            try {
                if (isNew) {
                    q.quoteNo = nextQuoteNo(con, q.quoteDate);
                    insertHeader(con, q);
                } else {
                    updateHeader(con, q);
                    try (PreparedStatement del = con.prepareStatement(
                            "DELETE FROM quotation_items WHERE quotation_id = ?")) {
                        del.setInt(1, q.id);
                        del.executeUpdate();
                    }
                }
                insertItems(con, q);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                if (isNew) {
                    q.id = null;
                    q.quoteNo = null;
                }
                throw e;
            }
        }
    }

    public static Quotation find(int id) throws SQLException {
        try (Connection con = Db.get()) {
            Quotation q;
            try (PreparedStatement ps = con.prepareStatement("SELECT * FROM quotations WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    q = mapHeader(rs);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT * FROM quotation_items WHERE quotation_id = ? ORDER BY line_no")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        QuoteItem it = new QuoteItem();
                        it.description = rs.getString("description");
                        it.unit = rs.getString("unit");
                        it.qty = rs.getBigDecimal("qty");
                        it.unitPrice = rs.getBigDecimal("unit_price");
                        it.gstRate = rs.getBigDecimal("gst_rate");
                        q.items.add(it);
                    }
                }
            }
            return q;
        }
    }

    /** Headers only (no items), newest first. Search matches quote no, customer name or phone. */
    public static List<Quotation> search(String text, String status, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM quotations WHERE 1=1");
        boolean hasText = text != null && !text.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasText) sql.append(" AND (quote_no LIKE ? OR customer_name LIKE ? OR customer_phone LIKE ?)");
        if (hasStatus) sql.append(" AND status = ?");
        sql.append(" ORDER BY quote_date DESC, id DESC LIMIT ").append(limit);

        List<Quotation> list = new ArrayList<>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int i = 1;
            if (hasText) {
                String like = "%" + text.trim() + "%";
                ps.setString(i++, like);
                ps.setString(i++, like);
                ps.setString(i++, like);
            }
            if (hasStatus) ps.setString(i, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapHeader(rs));
            }
        }
        return list;
    }

    public static void updateStatus(int id, String status) throws SQLException {
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement("UPDATE quotations SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public static void delete(int id) throws SQLException {
        try (Connection con = Db.get();
             PreparedStatement ps = con.prepareStatement("DELETE FROM quotations WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Dashboard figures
    // ------------------------------------------------------------------

    public static class Stats {
        public int totalQuotes;
        public int monthQuotes;
        public BigDecimal monthValue = BigDecimal.ZERO;
        public int accepted;
        public int pending;          // DRAFT + SENT
        public BigDecimal acceptedValue = BigDecimal.ZERO;
        /** Quoted value per month for the last 6 months, oldest first. */
        public Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();
    }

    public static Stats stats() throws SQLException {
        Stats s = new Stats();
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        try (Connection con = Db.get()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*),"
                    + " SUM(quote_date >= ?),"
                    + " COALESCE(SUM(CASE WHEN quote_date >= ? THEN grand_total END), 0),"
                    + " SUM(status = 'ACCEPTED'),"
                    + " SUM(status IN ('DRAFT','SENT')),"
                    + " COALESCE(SUM(CASE WHEN status = 'ACCEPTED' THEN grand_total END), 0)"
                    + " FROM quotations")) {
                ps.setDate(1, Date.valueOf(monthStart));
                ps.setDate(2, Date.valueOf(monthStart));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    s.totalQuotes = rs.getInt(1);
                    s.monthQuotes = rs.getInt(2);
                    s.monthValue = rs.getBigDecimal(3);
                    s.accepted = rs.getInt(4);
                    s.pending = rs.getInt(5);
                    s.acceptedValue = rs.getBigDecimal(6);
                }
            }

            YearMonth now = YearMonth.now();
            for (int i = 5; i >= 0; i--) s.monthly.put(now.minusMonths(i), BigDecimal.ZERO);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT YEAR(quote_date), MONTH(quote_date), SUM(grand_total) FROM quotations"
                    + " WHERE quote_date >= ? GROUP BY YEAR(quote_date), MONTH(quote_date)")) {
                ps.setDate(1, Date.valueOf(now.minusMonths(5).atDay(1)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        s.monthly.put(YearMonth.of(rs.getInt(1), rs.getInt(2)), rs.getBigDecimal(3));
                    }
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Indian financial year, April–March: 2026-09-24 → "2026-27". */
    static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return start + "-" + String.format("%02d", (start + 1) % 100);
    }

    /** Atomically takes the next number for the FY, e.g. SQ/2026-27/0007. */
    private static String nextQuoteNo(Connection con, LocalDate date) throws SQLException {
        String fy = financialYear(date);
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO quote_counter (fy, last_no) VALUES (?, LAST_INSERT_ID(1)) "
                + "ON DUPLICATE KEY UPDATE last_no = LAST_INSERT_ID(last_no + 1)")) {
            ps.setString(1, fy);
            ps.executeUpdate();
        }
        int no;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            no = rs.getInt(1);
        }
        String prefix = Settings.get("quote.prefix").isBlank() ? "SQ" : Settings.get("quote.prefix").trim();
        return String.format("%s/%s/%04d", prefix, fy, no);
    }

    private static void insertHeader(Connection con, Quotation q) throws SQLException {
        String sql = "INSERT INTO quotations (quote_no, quote_date, valid_until, customer_name, customer_phone,"
                + " customer_email, customer_address, system_type, capacity_kw, discount_pct, subtotal,"
                + " discount_amount, gst_total, round_off, grand_total, subsidy, show_subsidy, status, terms)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, q.quoteNo);
            bindHeader(ps, q, 2);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                q.id = keys.getInt(1);
            }
        }
    }

    private static void updateHeader(Connection con, Quotation q) throws SQLException {
        String sql = "UPDATE quotations SET quote_date=?, valid_until=?, customer_name=?, customer_phone=?,"
                + " customer_email=?, customer_address=?, system_type=?, capacity_kw=?, discount_pct=?,"
                + " subtotal=?, discount_amount=?, gst_total=?, round_off=?, grand_total=?, subsidy=?,"
                + " show_subsidy=?, status=?, terms=? WHERE id=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int next = bindHeader(ps, q, 1);
            ps.setInt(next, q.id);
            ps.executeUpdate();
        }
    }

    /** Binds the 18 shared header columns starting at index {@code i}; returns the next free index. */
    private static int bindHeader(PreparedStatement ps, Quotation q, int i) throws SQLException {
        ps.setDate(i++, Date.valueOf(q.quoteDate));
        ps.setDate(i++, Date.valueOf(q.validUntil));
        ps.setString(i++, q.customerName);
        ps.setString(i++, q.customerPhone);
        ps.setString(i++, q.customerEmail);
        ps.setString(i++, q.customerAddress);
        ps.setString(i++, q.systemType);
        ps.setBigDecimal(i++, q.capacityKw);
        ps.setBigDecimal(i++, q.discountPct);
        ps.setBigDecimal(i++, q.subtotal);
        ps.setBigDecimal(i++, q.discountAmount);
        ps.setBigDecimal(i++, q.gstTotal);
        ps.setBigDecimal(i++, q.roundOff);
        ps.setBigDecimal(i++, q.grandTotal);
        ps.setBigDecimal(i++, q.subsidy);
        ps.setBoolean(i++, q.showSubsidy);
        ps.setString(i++, q.status);
        ps.setString(i++, q.terms);
        return i;
    }

    private static void insertItems(Connection con, Quotation q) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO quotation_items (quotation_id, line_no, description, unit, qty, unit_price, gst_rate)"
                + " VALUES (?,?,?,?,?,?,?)")) {
            int line = 1;
            for (QuoteItem it : q.items) {
                ps.setInt(1, q.id);
                ps.setInt(2, line++);
                ps.setString(3, it.description);
                ps.setString(4, it.unit);
                ps.setBigDecimal(5, it.qty);
                ps.setBigDecimal(6, it.unitPrice);
                ps.setBigDecimal(7, it.gstRate);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static Quotation mapHeader(ResultSet rs) throws SQLException {
        Quotation q = new Quotation();
        q.id = rs.getInt("id");
        q.quoteNo = rs.getString("quote_no");
        q.quoteDate = rs.getDate("quote_date").toLocalDate();
        q.validUntil = rs.getDate("valid_until").toLocalDate();
        q.customerName = rs.getString("customer_name");
        q.customerPhone = nz(rs.getString("customer_phone"));
        q.customerEmail = nz(rs.getString("customer_email"));
        q.customerAddress = nz(rs.getString("customer_address"));
        q.systemType = rs.getString("system_type");
        q.capacityKw = rs.getBigDecimal("capacity_kw");
        q.discountPct = rs.getBigDecimal("discount_pct");
        q.subtotal = rs.getBigDecimal("subtotal");
        q.discountAmount = rs.getBigDecimal("discount_amount");
        q.gstTotal = rs.getBigDecimal("gst_total");
        q.roundOff = rs.getBigDecimal("round_off");
        q.grandTotal = rs.getBigDecimal("grand_total");
        q.subsidy = rs.getBigDecimal("subsidy");
        q.showSubsidy = rs.getBoolean("show_subsidy");
        q.status = rs.getString("status");
        q.terms = nz(rs.getString("terms"));
        return q;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
