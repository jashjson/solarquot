package com.solarquote.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Hands out JDBC connections and creates the schema on first run.
 * A desktop app talking to a local MySQL does not need a pool: each DAO call
 * opens a short-lived connection in try-with-resources.
 */
public final class Db {

    private static DbConfig config;

    private Db() {}

    public static void configure(DbConfig c) {
        config = c;
    }

    public static DbConfig config() {
        return config;
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user, config.password);
    }

    /** Throws if the given settings cannot connect. Used by the connection dialog. */
    public static void test(DbConfig c) throws SQLException {
        try (Connection con = DriverManager.getConnection(c.url(), c.user, c.password)) {
            con.isValid(3);
        }
    }

    // ------------------------------------------------------------------
    // Schema
    // ------------------------------------------------------------------

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS settings (
            skey   VARCHAR(60) PRIMARY KEY,
            svalue TEXT
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS products (
            id         INT AUTO_INCREMENT PRIMARY KEY,
            name       VARCHAR(160) NOT NULL,
            category   VARCHAR(30)  NOT NULL,
            brand      VARCHAR(60),
            unit       VARCHAR(10)  NOT NULL DEFAULT 'Nos',
            price      DECIMAL(12,2) NOT NULL,
            gst_rate   DECIMAL(5,2)  NOT NULL,
            wattage    INT,
            active     TINYINT(1) NOT NULL DEFAULT 1
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS quote_counter (
            fy      VARCHAR(7) PRIMARY KEY,
            last_no INT NOT NULL
        ) ENGINE=InnoDB
        """,
        """
        CREATE TABLE IF NOT EXISTS quotations (
            id              INT AUTO_INCREMENT PRIMARY KEY,
            quote_no        VARCHAR(40) NOT NULL UNIQUE,
            quote_date      DATE NOT NULL,
            valid_until     DATE NOT NULL,
            customer_name   VARCHAR(120) NOT NULL,
            customer_phone  VARCHAR(20),
            customer_email  VARCHAR(120),
            customer_address VARCHAR(400),
            system_type     VARCHAR(10) NOT NULL,
            capacity_kw     DECIMAL(6,2) NOT NULL,
            discount_pct    DECIMAL(5,2) NOT NULL DEFAULT 0,
            subtotal        DECIMAL(14,2) NOT NULL DEFAULT 0,
            discount_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
            gst_total       DECIMAL(14,2) NOT NULL DEFAULT 0,
            round_off       DECIMAL(6,2)  NOT NULL DEFAULT 0,
            grand_total     DECIMAL(14,2) NOT NULL DEFAULT 0,
            subsidy         DECIMAL(12,2) NOT NULL DEFAULT 0,
            show_subsidy    TINYINT(1) NOT NULL DEFAULT 1,
            status          VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
            terms           TEXT,
            created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_q_date (quote_date),
            INDEX idx_q_status (status)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """,
        """
        CREATE TABLE IF NOT EXISTS quotation_items (
            id           INT AUTO_INCREMENT PRIMARY KEY,
            quotation_id INT NOT NULL,
            line_no      INT NOT NULL,
            description  VARCHAR(300) NOT NULL,
            unit         VARCHAR(10) NOT NULL,
            qty          DECIMAL(10,2) NOT NULL,
            unit_price   DECIMAL(12,2) NOT NULL,
            gst_rate     DECIMAL(5,2) NOT NULL,
            FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    };

    private static final String[][] DEFAULT_SETTINGS = {
        {"company.name", "Your Solar Company"},
        {"company.address", "Street, City, State - PIN"},
        {"company.phone", ""},
        {"company.email", ""},
        {"company.website", ""},
        {"company.gstin", ""},
        {"company.logo", ""},
        {"bank.name", ""},
        {"bank.account_name", ""},
        {"bank.account_no", ""},
        {"bank.ifsc", ""},
        {"quote.prefix", "SQ"},
        {"quote.validity_days", "30"},
        {"quote.terms",
            "1. This quotation is valid for the period mentioned above.\n"
          + "2. Payment: 40% advance on order, 50% before delivery, 10% after commissioning.\n"
          + "3. Installation will be completed within 15 working days of advance payment.\n"
          + "4. Net metering application and DISCOM charges are extra unless stated.\n"
          + "5. Warranty as per manufacturer: panels 25 years (performance), inverter 5-10 years.\n"
          + "6. Subsidy (if shown) is indicative and credited by the government directly to the customer."},
        {"pdf.folder", System.getProperty("user.home") + "/Documents/SolarQuotes"},
        {"subsidy.enabled", "true"},
        {"subsidy.rate_upto2", "30000"},
        {"subsidy.rate_2to3", "18000"},
        {"subsidy.cap", "78000"},
    };

    // name, category, brand, unit, price, gst, wattage
    private static final Object[][] DEFAULT_PRODUCTS = {
        {"Mono PERC Half-cut Solar Panel 540Wp", "PANEL", "Waaree", "Nos", 14500, 12, 540},
        {"TOPCon Bifacial Solar Panel 580Wp", "PANEL", "Adani", "Nos", 16800, 12, 580},
        {"On-Grid Inverter 3kW", "INVERTER", "Growatt", "Nos", 32000, 12, null},
        {"On-Grid Inverter 5kW", "INVERTER", "Growatt", "Nos", 45000, 12, null},
        {"Hybrid Inverter 5kW", "INVERTER", "Luminous", "Nos", 68000, 12, null},
        {"Lithium Battery 5kWh", "BATTERY", "Luminous", "Nos", 125000, 18, null},
        {"GI Mounting Structure (per kW)", "STRUCTURE", "", "kW", 6500, 18, null},
        {"DC/AC Cables, MC4 Connectors & Conduits", "CABLE", "Polycab", "Set", 9500, 18, null},
        {"ACDB/DCDB, Earthing & Lightning Arrester", "BOS", "", "Set", 12000, 18, null},
        {"Installation & Commissioning (per kW)", "SERVICE", "", "kW", 4000, 18, null},
        {"Net Metering Liaison", "SERVICE", "", "Nos", 5000, 18, null},
    };

    public static void initSchema() throws SQLException {
        try (Connection con = get(); Statement st = con.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
            // Insert defaults only for keys that don't exist yet
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT IGNORE INTO settings (skey, svalue) VALUES (?, ?)")) {
                for (String[] kv : DEFAULT_SETTINGS) {
                    ps.setString(1, kv[0]);
                    ps.setString(2, kv[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            // Seed a starter catalog on an empty database
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                if (rs.getInt(1) == 0) {
                    seedProducts(con);
                }
            }
        }
    }

    private static void seedProducts(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO products (name, category, brand, unit, price, gst_rate, wattage) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            for (Object[] p : DEFAULT_PRODUCTS) {
                ps.setString(1, (String) p[0]);
                ps.setString(2, (String) p[1]);
                ps.setString(3, (String) p[2]);
                ps.setString(4, (String) p[3]);
                ps.setInt(5, (Integer) p[4]);
                ps.setInt(6, (Integer) p[5]);
                if (p[6] == null) ps.setNull(7, java.sql.Types.INTEGER);
                else ps.setInt(7, (Integer) p[6]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
