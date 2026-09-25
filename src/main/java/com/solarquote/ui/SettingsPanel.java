package com.solarquote.ui;

import com.solarquote.dao.SettingsDao;
import com.solarquote.db.Db;
import com.solarquote.db.DbConfig;
import com.solarquote.service.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Company letterhead, bank details, quotation defaults, subsidy rates and DB connection. */
public class SettingsPanel extends JPanel implements MainFrame.Page {

    private final MainFrame frame;
    /** Setting key → text field, so load/save is one loop. */
    private final Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JTextArea terms = new JTextArea(10, 50);
    private final JCheckBox subsidyEnabled = new JCheckBox("Calculate PM Surya Ghar subsidy for on-grid systems");
    private final JLabel dbInfo = new JLabel();
    private final JLabel message = new JLabel(" ");

    public SettingsPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(Ui.BG);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Company", tab(companyForm(), "Printed at the top of every quotation."));
        tabs.addTab("Bank Details", tab(bankForm(), "Printed at the bottom of the quotation if an account number is set."));
        tabs.addTab("Quotation", tab(quotationForm(), null));
        tabs.addTab("Subsidy", tab(subsidyForm(),
                "Rates change periodically – verify with pmsuryaghar.gov.in before relying on them."));
        tabs.addTab("Database", tab(databaseForm(), null));

        JPanel card = Ui.card(null);
        card.add(tabs, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(message, BorderLayout.WEST);
        JButton save = Ui.primary("Save Settings");
        save.addActionListener(e -> save());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(save);
        bottom.add(right, BorderLayout.EAST);
        card.add(bottom, BorderLayout.SOUTH);

        add(Ui.page("Settings", null, card), BorderLayout.CENTER);
    }

    @Override
    public void onShow() {
        try {
            Settings.reload();
        } catch (Exception e) {
            Ui.error(this, "Could not load settings.", e);
        }
        fields.forEach((key, f) -> f.setText(Settings.get(key)));
        terms.setText(Settings.get("quote.terms"));
        terms.setCaretPosition(0);
        subsidyEnabled.setSelected(Settings.getBool("subsidy.enabled"));
        DbConfig c = Db.config();
        dbInfo.setText("<html>Connected to <b>" + c.database + "</b> on <b>" + c.host + ":" + c.port
                + "</b> as <b>" + c.user + "</b></html>");
        message.setText(" ");
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private JComponent companyForm() {
        Ui.Form f = new Ui.Form()
                .add("Company name", field("company.name"))
                .add("Address", field("company.address"))
                .add("Phone", field("company.phone"))
                .add("Email", field("company.email"))
                .add("Website", field("company.website"))
                .add("GSTIN", field("company.gstin"))
                .add("Logo (PNG/JPG)", withBrowse(field("company.logo"), false));
        return f.fillRemaining();
    }

    private JComponent bankForm() {
        return new Ui.Form()
                .add("Bank name", field("bank.name"))
                .add("Account name", field("bank.account_name"))
                .add("Account number", field("bank.account_no"))
                .add("IFSC", field("bank.ifsc"))
                .fillRemaining();
    }

    private JComponent quotationForm() {
        terms.setLineWrap(true);
        terms.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(terms);
        sp.setPreferredSize(new Dimension(500, 200));
        JTextField prefix = field("quote.prefix");
        prefix.setToolTipText("Quote numbers look like PREFIX/2026-27/0001");
        return new Ui.Form()
                .add("Quote number prefix", prefix)
                .add("Validity (days)", field("quote.validity_days"))
                .add("PDF save folder", withBrowse(field("pdf.folder"), true))
                .add("Default terms", sp)
                .fillRemaining();
    }

    private JComponent subsidyForm() {
        subsidyEnabled.setOpaque(false);
        return new Ui.Form()
                .add("", subsidyEnabled)
                .add("Rate per kW, first 2 kW (₹)", field("subsidy.rate_upto2"))
                .add("Rate per kW, 2–3 kW (₹)", field("subsidy.rate_2to3"))
                .add("Maximum subsidy (₹)", field("subsidy.cap"))
                .fillRemaining();
    }

    private JComponent databaseForm() {
        JButton change = Ui.button("Change connection…");
        change.addActionListener(e -> {
            DbConfig c = DbConfigDialog.show(SwingUtilities.getWindowAncestor(this), Db.config(), null);
            if (c != null) {
                Ui.warn(this, "Connection saved. Please restart SolarQuote to use the new database.");
            }
        });
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(change);
        return new Ui.Form()
                .add("Current", dbInfo)
                .add("", row)
                .add("Config file", Ui.muted(DbConfig.FILE.toString()))
                .fillRemaining();
    }

    private static JComponent tab(JComponent form, String hint) {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(BorderFactory.createEmptyBorder(16, 8, 8, 8));
        p.setOpaque(false);
        if (hint != null) p.add(Ui.muted(hint), BorderLayout.NORTH);
        p.add(form, BorderLayout.CENTER);
        return p;
    }

    private JTextField field(String key) {
        JTextField f = new JTextField(30);
        fields.put(key, f);
        return f;
    }

    private JComponent withBrowse(JTextField f, boolean directory) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        p.add(f, BorderLayout.CENTER);
        JButton browse = Ui.button("Browse…");
        browse.addActionListener(e -> {
            JFileChooser ch = new JFileChooser(f.getText().isBlank() ? null : new File(f.getText()));
            if (directory) {
                ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else {
                ch.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG)", "png", "jpg", "jpeg"));
            }
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                f.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });
        p.add(browse, BorderLayout.EAST);
        return p;
    }

    // ------------------------------------------------------------------

    private void save() {
        String err = validateNumbers();
        if (err != null) {
            Ui.warn(this, err);
            return;
        }
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach((key, f) -> values.put(key, f.getText().trim()));
        values.put("quote.terms", terms.getText().trim());
        values.put("subsidy.enabled", String.valueOf(subsidyEnabled.isSelected()));
        try {
            SettingsDao.saveAll(values);
            Settings.reload();
            frame.refreshCompany();
            message.setForeground(Ui.GREEN);
            message.setText("✔ Settings saved.");
        } catch (Exception e) {
            Ui.error(this, "Could not save settings.", e);
        }
    }

    private String validateNumbers() {
        try {
            int days = Integer.parseInt(fields.get("quote.validity_days").getText().trim());
            if (days < 1) return "Validity must be at least 1 day.";
        } catch (NumberFormatException e) {
            return "Validity (days) must be a whole number.";
        }
        for (String k : new String[]{"subsidy.rate_upto2", "subsidy.rate_2to3", "subsidy.cap"}) {
            if (Ui.parseDecimal(fields.get(k).getText(), null) == null) {
                return "Subsidy amounts must be numbers.";
            }
        }
        if (fields.get("company.name").getText().isBlank()) return "Company name is required.";
        String prefix = fields.get("quote.prefix").getText().trim();
        if (prefix.isEmpty() || prefix.contains("/") || prefix.length() > 15) {
            return "Quote prefix must be 1–15 characters without '/'.";
        }
        return null;
    }
}
