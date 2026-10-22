package com.solarquote.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Embedded H2 database stored in the user's profile (no server, nothing to install).
 * One connection is kept open for the life of the app: it holds the file lock (so a second
 * copy of the app is detected) and keeps the database open between calls. Each DAO call
 * still gets its own short-lived connection, which is cheap for an embedded database.
 */
public final class Db {

    /** Thrown when another SolarQuote window already has the database open. */
    public static class AlreadyRunningException extends SQLException {
        public AlreadyRunningException(Throwable cause) {
            super("SolarQuote is already running.", cause);
        }
    }

    private static final int KEEP_BACKUPS = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static Path dataDir = defaultDataDir();
    private static Connection keepAlive;

    private Db() {}

    /** %LOCALAPPDATA%\SolarQuoteData on Windows (separate from the install folder), ~/.solarquote elsewhere. */
    public static Path defaultDataDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) return Path.of(local, "SolarQuoteData");
        return Path.of(System.getProperty("user.home"), ".solarquote");
    }

    public static Path dataDir() {
        return dataDir;
    }

    public static Path backupDir() {
        return dataDir.resolve("backups");
    }

    private static String url() {
        // DB_CLOSE_DELAY=-1: stay open between connections; we shut down explicitly on exit.
        return "jdbc:h2:file:" + dataDir.resolve("solarquote").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    /**
     * Opens (creating if needed) the database in {@code dir}, builds the schema and takes a
     * start-up backup. Call once, before any DAO.
     */
    public static synchronized void open(Path dir) throws SQLException {
        close();
        dataDir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new SQLException("Cannot create data folder " + dir + ": " + e.getMessage(), e);
        }
        try {
            keepAlive = DriverManager.getConnection(url(), "sa", "");
        } catch (SQLException e) {
            String m = String.valueOf(e.getMessage());
            if (e.getErrorCode() == 90020 || m.contains("already in use") || m.contains("Locked by another process")) {
                throw new AlreadyRunningException(e);
            }
            throw e;
        }
        initSchema();
        try {
            backup();
        } catch (SQLException | RuntimeException e) {
            // A failed backup must never stop the app from starting
            System.err.println("Backup skipped: " + e.getMessage());
        }
    }

    /** Cleanly shuts the database down so the files are consistent. Safe to call repeatedly. */
    public static synchronized void close() {
        if (keepAlive == null) return;
        try (Statement st = keepAlive.createStatement()) {
            st.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // already closed
        }
        try {
            keepAlive.close();
        } catch (SQLException ignored) {
            // nothing left to do
        }
        keepAlive = null;
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url(), "sa", "");
    }

    /** Writes a zip snapshot into the backups folder and prunes old ones. Returns the file. */
    public static synchronized Path backup() throws SQLException {
        Path dir = backupDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new SQLException("Cannot create backup folder " + dir + ": " + e.getMessage(), e);
        }
        Path file = dir.resolve("solarquote-" + LocalDateTime.now().format(STAMP) + ".zip");
        try (Connection con = get(); Statement st = con.createStatement()) {
            st.execute("BACKUP TO '" + file.toAbsolutePath().toString().replace('\\', '/').replace("'", "''") + "'");
        }
        prune(dir);
        return file;
    }

    private static void prune(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> zips = new ArrayList<>(files
                    .filter(f -> f.getFileName().toString().matches("solarquote-.*\\.zip"))
                    .sorted(Comparator.comparing((Path f) -> f.getFileName().toString()).reversed())
                    .toList());
            for (int i = KEEP_BACKUPS; i < zips.size(); i++) {
                Files.deleteIfExists(zips.get(i));
            }
        } catch (IOException ignored) {
            // pruning is best-effort
        }
    }

    // ------------------------------------------------------------------
    // Schema
    // ------------------------------------------------------------------

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS settings (
            skey   VARCHAR(60) PRIMARY KEY,
            svalue CLOB
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS products (
            id         INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            name       VARCHAR(160) NOT NULL,
            category   VARCHAR(30)  NOT NULL,
            brand      VARCHAR(60),
            unit       VARCHAR(10)  NOT NULL DEFAULT 'Nos',
            price      DECIMAL(12,2) NOT NULL,
            gst_rate   DECIMAL(5,2)  NOT NULL,
            wattage    INT,
            active     BOOLEAN NOT NULL DEFAULT TRUE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS quote_counter (
            fy      VARCHAR(7) PRIMARY KEY,
            last_no INT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS quotations (
            id              INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
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
            state_subsidy   DECIMAL(12,2) NOT NULL DEFAULT 0,
            show_subsidy    BOOLEAN NOT NULL DEFAULT TRUE,
            status          VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
            terms           CLOB,
            created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS quotation_items (
            id           INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
            quotation_id INT NOT NULL,
            line_no      INT NOT NULL,
            description  VARCHAR(300) NOT NULL,
            unit         VARCHAR(10) NOT NULL,
            qty          DECIMAL(10,2) NOT NULL,
            unit_price   DECIMAL(12,2) NOT NULL,
            gst_rate     DECIMAL(5,2) NOT NULL,
            FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE CASCADE
        )
        """,
        // Columns added after first release – existing databases pick them up here
        "ALTER TABLE quotations ADD COLUMN IF NOT EXISTS state_subsidy DECIMAL(12,2) NOT NULL DEFAULT 0",
        "CREATE INDEX IF NOT EXISTS idx_q_date ON quotations(quote_date)",
        "CREATE INDEX IF NOT EXISTS idx_q_status ON quotations(status)",
        "CREATE INDEX IF NOT EXISTS idx_items_q ON quotation_items(quotation_id)"
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
        {"ui.theme", "light"},
        {"quote.prefix", "SQ"},
        {"quote.validity_days", "30"},
        {"quote.terms",
            "1. This quotation is valid for the period mentioned above.\n"
          + "2. Payment: 40% advance on order, 50% before delivery, 10% after commissioning.\n"
          + "3. Installation will be completed within 15 working days of advance payment.\n"
          + "4. Net metering application and DISCOM charges are extra unless stated.\n"
          + "5. Warranty as per manufacturer: panels 25 years (performance), inverter 5-10 years.\n"
          + "6. Subsidy (if shown) is indicative and credited by the government directly to the customer."},
        {"pdf.folder", Path.of(System.getProperty("user.home"), "Documents", "SolarQuotes").toString()},
        {"subsidy.enabled", "true"},
        {"subsidy.rate_upto2", "30000"},
        {"subsidy.rate_2to3", "18000"},
        {"subsidy.cap", "78000"},
        // State top-up for systems up to 3 kW (₹5k/₹10k/₹22k at 1/2/3 kW, per crystalgrid.in/subsidy, Oct 2026)
        {"subsidy.state_enabled", "true"},
        {"subsidy.state_rate_upto2", "5000"},
        {"subsidy.state_rate_2to3", "12000"},
        {"subsidy.state_cap", "22000"},
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

    private static void initSchema() throws SQLException {
        try (Connection con = get(); Statement st = con.createStatement()) {
            for (String ddl : SCHEMA) {
                st.execute(ddl);
            }
            // Insert defaults only for keys that don't exist yet
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO settings (skey, svalue) SELECT CAST(? AS VARCHAR(60)), CAST(? AS VARCHAR) FROM DUAL "
                    + "WHERE NOT EXISTS (SELECT 1 FROM settings WHERE skey = ?)")) {
                for (String[] kv : DEFAULT_SETTINGS) {
                    ps.setString(1, kv[0]);
                    ps.setString(2, kv[1]);
                    ps.setString(3, kv[0]);
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
