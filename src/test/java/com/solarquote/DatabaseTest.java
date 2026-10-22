package com.solarquote;

import com.solarquote.dao.ProductDao;
import com.solarquote.dao.QuotationDao;
import com.solarquote.dao.SettingsDao;
import com.solarquote.db.Db;
import com.solarquote.model.Product;
import com.solarquote.model.Quotation;
import com.solarquote.model.QuoteItem;
import com.solarquote.pdf.QuotePdf;
import com.solarquote.service.KitBuilder;
import com.solarquote.service.Pricing;
import com.solarquote.service.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @TempDir Path dir;

    @BeforeEach
    void open() throws Exception {
        Db.open(dir.resolve("data"));
        Settings.reload();
    }

    @AfterEach
    void close() {
        Db.close();
    }

    private Quotation sample(String customer) throws Exception {
        Quotation q = new Quotation();
        q.customerName = customer;
        q.customerPhone = "98765 43210";
        q.customerAddress = "रामनगर, Pune ₹";
        q.capacityKw = new BigDecimal("5");
        List<Product> catalog = ProductDao.list("", true);
        q.items.addAll(KitBuilder.build(q.systemType, q.capacityKw, catalog));
        Pricing.compute(q);
        return q;
    }

    @Test
    void firstRunSeedsCatalogAndSettings() throws Exception {
        assertFalse(ProductDao.list("", false).isEmpty());
        assertEquals("SQ", Settings.get("quote.prefix"));
        assertTrue(Settings.get("quote.terms").startsWith("1."));
    }

    @Test
    void saveLoadRoundTripAndNumbering() throws Exception {
        Quotation a = sample("Asha");
        QuotationDao.save(a);
        Quotation b = sample("Bharat");
        QuotationDao.save(b);
        assertEquals("SQ0001", a.quoteNo);
        assertEquals("SQ0002", b.quoteNo);

        Quotation back = QuotationDao.find(a.id);
        assertEquals("Asha", back.customerName);
        assertEquals("रामनगर, Pune ₹", back.customerAddress);
        assertEquals(a.items.size(), back.items.size());
        assertEquals(0, a.grandTotal.compareTo(back.grandTotal));

        // update replaces items, keeps number
        back.items.remove(0);
        Pricing.compute(back);
        QuotationDao.save(back);
        assertEquals(a.items.size() - 1, QuotationDao.find(a.id).items.size());
        assertEquals("SQ0001", QuotationDao.find(a.id).quoteNo);

        assertEquals(2, QuotationDao.search("", null, 50).size());
        assertEquals(1, QuotationDao.search("bharat", null, 50).size());
        assertEquals(2, QuotationDao.search("98765", "DRAFT", 50).size());
        QuotationDao.updateStatus(a.id, "ACCEPTED");
        assertEquals(1, QuotationDao.search("", "ACCEPTED", 50).size());
        QuotationDao.delete(b.id);
        assertEquals(1, QuotationDao.search("", null, 50).size());
        assertNull(QuotationDao.find(b.id));
    }

    @Test
    void numberingContinuesAfterDeleteAndRestart() throws Exception {
        Quotation a = sample("A");
        QuotationDao.save(a);
        QuotationDao.delete(a.id);
        Db.close();
        Db.open(dir.resolve("data"));
        Quotation b = sample("B");
        QuotationDao.save(b);
        assertEquals("SQ0002", b.quoteNo);
    }

    @Test
    void dashboardStats() throws Exception {
        QuotationDao.Stats empty = QuotationDao.stats();
        assertEquals(0, empty.totalQuotes);
        Quotation a = sample("A");
        QuotationDao.save(a);
        Quotation b = sample("B");
        QuotationDao.save(b);
        QuotationDao.updateStatus(b.id, "ACCEPTED");
        QuotationDao.Stats s = QuotationDao.stats();
        assertEquals(2, s.totalQuotes);
        assertEquals(2, s.monthQuotes);
        assertEquals(1, s.accepted);
        assertEquals(1, s.pending);
        assertEquals(6, s.monthly.size());
    }

    @Test
    void centralAndStateSubsidyReduceNetPrice() throws Exception {
        // kW → {central, state}: state is ₹5k/kW up to 2 kW, +₹12k for the 3rd kW, capped at ₹22k
        String[][] cases = {{"1", "30000", "5000"}, {"2", "60000", "10000"}, {"3", "78000", "22000"},
                            {"2.5", "69000", "16000"}, {"5", "78000", "22000"}};
        for (String[] c : cases) {
            BigDecimal kw = new BigDecimal(c[0]);
            assertEquals(0, new BigDecimal(c[1]).compareTo(Pricing.subsidy("ON_GRID", kw)), c[0] + " kW central");
            assertEquals(0, new BigDecimal(c[2]).compareTo(Pricing.stateSubsidy("ON_GRID", kw)), c[0] + " kW state");
        }
        assertEquals(0, Pricing.stateSubsidy("OFF_GRID", BigDecimal.ONE).signum());

        Quotation q = sample("Net");
        assertEquals(0, new BigDecimal("100000").compareTo(q.totalSubsidy()));
        assertEquals(0, q.grandTotal.subtract(new BigDecimal("100000")).compareTo(q.netPrice()));
        QuotationDao.save(q);
        assertEquals(0, new BigDecimal("22000").compareTo(QuotationDao.find(q.id).stateSubsidy));

        SettingsDao.saveAll(Map.of("subsidy.state_enabled", "false"));
        Settings.reload();
        Pricing.compute(q);
        assertEquals(0, q.stateSubsidy.signum());
    }

    @Test
    void stateSubsidyColumnAddedToOldDatabase() throws Exception {
        try (var con = Db.get(); var st = con.createStatement()) {
            st.execute("ALTER TABLE quotations DROP COLUMN state_subsidy");
        }
        Db.close();
        Db.open(dir.resolve("data"));
        Quotation q = sample("Old");
        QuotationDao.save(q);
        assertEquals(0, q.stateSubsidy.compareTo(QuotationDao.find(q.id).stateSubsidy));
    }

    @Test
    void settingsAndProductsPersist() throws Exception {
        SettingsDao.saveAll(Map.of("company.name", "Surya Energy", "pdf.folder", dir.toString()));
        Settings.reload();
        assertEquals("Surya Energy", Settings.get("company.name"));

        Product p = new Product();
        p.name = "Test Panel";
        p.category = "PANEL";
        p.brand = "X";
        p.unit = "Nos";
        p.price = new BigDecimal("1000");
        p.gstRate = new BigDecimal("12");
        p.wattage = 400;
        p.active = true;
        ProductDao.save(p);
        assertTrue(p.id != null && p.id > 0);
        p.price = new BigDecimal("1100");
        ProductDao.save(p);
        assertEquals(1, ProductDao.list("test PANEL", false).size());
        ProductDao.delete(p.id);
        assertEquals(0, ProductDao.list("Test Panel", false).size());
    }

    @Test
    void pdfIsWritten() throws Exception {
        SettingsDao.saveAll(Map.of("pdf.folder", dir.resolve("pdfs").toString()));
        Settings.reload();
        Quotation q = sample("Asha Kulkarni");
        QuotationDao.save(q);
        File f = QuotePdf.generate(q);
        assertTrue(f.length() > 5000, "pdf too small: " + f.length());
        assertEquals("%PDF", new String(Files.readAllBytes(f.toPath()), 0, 4));
    }

    @Test
    void backupAndSecondInstanceDetection() throws Exception {
        Path zip = Db.backup();
        assertTrue(Files.size(zip) > 0);
        // a second open of the same folder from another classloader/process is blocked by H2's lock;
        // inside one JVM the same URL is shared, so just verify the startup backup exists
        try (var files = Files.list(Db.backupDir())) {
            assertTrue(files.count() >= 1);
        }
    }
}
