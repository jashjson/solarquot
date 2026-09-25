package com.solarquote.ui;

import com.solarquote.dao.ProductDao;
import com.solarquote.dao.QuotationDao;
import com.solarquote.model.Product;
import com.solarquote.model.Quotation;
import com.solarquote.model.QuoteItem;
import com.solarquote.pdf.QuotePdf;
import com.solarquote.pdf.QuotePrinter;
import com.solarquote.service.KitBuilder;
import com.solarquote.service.Money;
import com.solarquote.service.Pricing;
import com.solarquote.service.Settings;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Create / edit a quotation: customer, system, line items, live totals, save, PDF and print. */
public class QuotationPanel extends JPanel implements MainFrame.Page {

    private static final String[] SYSTEM_LABELS = {"On-Grid", "Off-Grid", "Hybrid"};

    private final MainFrame frame;
    private Quotation current;
    private boolean dirty;
    private boolean loading;

    // Header
    private final JLabel titleLabel = Ui.title("New Quotation");
    private final JLabel quoteNoLabel = Ui.muted("");
    private final JComboBox<String> status = new JComboBox<>(Quotation.STATUSES);

    // Customer
    private final JTextField name = new JTextField();
    private final JTextField phone = new JTextField();
    private final JTextField email = new JTextField();
    private final JTextField address = new JTextField();

    // System
    private final JComboBox<String> systemType = new JComboBox<>(SYSTEM_LABELS);
    private final JSpinner capacity = new JSpinner(new SpinnerNumberModel(3.0, 0.5, 5000.0, 0.5));
    private final JSpinner date = new JSpinner(new SpinnerDateModel());
    private final JSpinner validDays = new JSpinner(new SpinnerNumberModel(30, 1, 365, 1));

    // Items
    private final ItemsModel itemsModel = new ItemsModel();
    private final JTable itemsTable = new JTable(itemsModel);
    private final JComboBox<Product> productCombo = new JComboBox<>();
    private final JSpinner addQty = new JSpinner(new SpinnerNumberModel(1.0, 0.01, 100000.0, 1.0));

    // Totals
    private final JSpinner discount = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.5));
    private final JCheckBox showSubsidy = new JCheckBox("Show subsidy on PDF", true);
    private final JLabel subtotalL = valueLabel();
    private final JLabel discountL = valueLabel();
    private final JLabel taxableL = valueLabel();
    private final JLabel gstL = valueLabel();
    private final JLabel roundL = valueLabel();
    private final JLabel grandL = valueLabel();
    private final JTextArea wordsL = new JTextArea(2, 20);
    private final JLabel subsidyL = valueLabel();
    private final JLabel netL = valueLabel();

    private final JTextArea terms = new JTextArea(4, 40);
    private final JLabel message = new JLabel(" ");

    public QuotationPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout(0, 14));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        add(buildTop(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        JPanel infoRow = new JPanel(new GridLayout(1, 2, 14, 0));
        infoRow.setOpaque(false);
        infoRow.add(buildCustomerCard());
        infoRow.add(buildSystemCard());
        body.add(infoRow, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(14, 0));
        middle.setOpaque(false);
        middle.add(buildItemsCard(), BorderLayout.CENTER);
        middle.add(buildTotalsCard(), BorderLayout.EAST);
        body.add(middle, BorderLayout.CENTER);
        body.add(buildTermsCard(), BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);

        add(buildActions(), BorderLayout.SOUTH);
        installChangeTracking();
        load(null);
    }

    // ------------------------------------------------------------------
    // Page lifecycle
    // ------------------------------------------------------------------

    @Override
    public void onShow() {
        reloadProducts();
        name.requestFocusInWindow();
    }

    /** Loads a quotation into the form; null starts a blank one with defaults from Settings. */
    public void load(Quotation q) {
        loading = true;
        try {
            if (q == null) {
                q = new Quotation();
                int days = Settings.getInt("quote.validity_days", 30);
                q.validUntil = q.quoteDate.plusDays(days);
                q.terms = Settings.get("quote.terms");
            }
            current = q;
            name.setText(q.customerName);
            phone.setText(q.customerPhone);
            email.setText(q.customerEmail);
            address.setText(q.customerAddress);
            systemType.setSelectedIndex(indexOf(Quotation.SYSTEM_TYPES, q.systemType));
            capacity.setValue(q.capacityKw.doubleValue());
            date.setValue(toDate(q.quoteDate));
            validDays.setValue((int) Math.max(1, ChronoUnit.DAYS.between(q.quoteDate, q.validUntil)));
            discount.setValue(q.discountPct.doubleValue());
            showSubsidy.setSelected(q.showSubsidy);
            status.setSelectedItem(q.status);
            terms.setText(q.terms);
            terms.setCaretPosition(0);
            itemsModel.setItems(q.items);
            message.setText(" ");
        } finally {
            loading = false;
        }
        // A copied quotation (id == null but has content) counts as unsaved work
        dirty = q.id == null && !q.items.isEmpty();
        updateTitle();
        recompute();
    }

    /** Called when the user clicks "New Quotation" in the sidebar. */
    public void startFreshIfClean() {
        if (!dirty) load(null);
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private JComponent buildTop() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(titleLabel);
        quoteNoLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        left.add(quoteNoLabel);
        top.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(Ui.muted("Status"));
        status.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (!sel && value != null) setForeground(Ui.statusColor(value.toString()));
                setFont(getFont().deriveFont(Font.BOLD));
                return this;
            }
        });
        right.add(status);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    private JComponent buildCustomerCard() {
        JPanel card = Ui.card("Customer");
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        name.putClientProperty("JTextField.placeholderText", "Full name (required)");
        phone.putClientProperty("JTextField.placeholderText", "10-digit mobile");
        email.putClientProperty("JTextField.placeholderText", "optional");
        address.putClientProperty("JTextField.placeholderText", "Installation address");
        addField(grid, 0, 0, "Name *", name);
        addField(grid, 0, 2, "Phone", phone);
        addField(grid, 1, 0, "Email", email);
        addField(grid, 1, 2, "Address", address);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSystemCard() {
        JPanel card = Ui.card("Solar System");
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        date.setEditor(new JSpinner.DateEditor(date, "dd-MM-yyyy"));
        addField(grid, 0, 0, "Type", systemType);
        addField(grid, 0, 2, "Capacity (kW)", capacity);
        addField(grid, 1, 0, "Quote date", date);
        addField(grid, 1, 2, "Valid (days)", validDays);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private static void addField(JPanel grid, int row, int col, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = col;
        c.insets = new Insets(4, col == 0 ? 0 : 14, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(label);
        l.setForeground(Ui.MUTED);
        grid.add(l, c);
        c.gridx = col + 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        field.setPreferredSize(new Dimension(120, field.getPreferredSize().height));
        grid.add(field, c);
    }

    private JComponent buildItemsCard() {
        JPanel card = Ui.card(null);

        // Row 1: heading + line tools; row 2: pick a product and add it
        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.setOpaque(false);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        addRow.setOpaque(false);
        addRow.add(Ui.muted("Product"));
        productCombo.setPreferredSize(new Dimension(420, productCombo.getPreferredSize().height));
        productCombo.setMaximumRowCount(16);
        productCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof Product p) {
                    setText("<html><b>" + p.displayName() + "</b> &nbsp;<font color='#6B7785'>"
                            + p.category + " · ₹" + Money.fmt(p.price) + "/" + p.unit + "</font></html>");
                }
                return this;
            }
        });
        addRow.add(productCombo);
        addQty.setPreferredSize(new Dimension(70, addQty.getPreferredSize().height));
        addRow.add(Ui.muted("Qty"));
        addRow.add(addQty);
        JButton add = Ui.primary("Add");
        add.addActionListener(e -> addSelectedProduct());
        addRow.add(add);
        toolbar.add(Ui.heading("Items"), BorderLayout.WEST);
        toolbar.add(addRow, BorderLayout.SOUTH);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        tools.setOpaque(false);
        JButton kit = Ui.button("⚡ Auto-fill kit");
        kit.setToolTipText("Fill panels, inverter, structure, cables and installation for the chosen capacity");
        kit.addActionListener(e -> autoFillKit());
        JButton custom = Ui.button("+ Custom line");
        custom.addActionListener(e -> addCustomLine());
        JButton up = Ui.button("↑");
        up.setToolTipText("Move up");
        up.addActionListener(e -> moveSelected(-1));
        JButton down = Ui.button("↓");
        down.setToolTipText("Move down");
        down.addActionListener(e -> moveSelected(1));
        JButton remove = Ui.danger("Remove");
        remove.addActionListener(e -> removeSelected());
        tools.add(kit);
        tools.add(custom);
        tools.add(up);
        tools.add(down);
        tools.add(remove);
        toolbar.add(tools, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);

        Ui.styleTable(itemsTable);
        itemsTable.setAutoCreateRowSorter(false);
        itemsTable.setRowSorter(null);           // line order matters
        itemsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        itemsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        itemsTable.setSurrendersFocusOnKeystroke(true);
        int[] widths = {36, 380, 70, 60, 110, 60, 120};
        for (int i = 0; i < widths.length; i++) {
            itemsTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        itemsTable.getColumnModel().getColumn(0).setMaxWidth(40);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultTableCellRenderer plainNumber = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : "");
            }
        };
        plainNumber.setHorizontalAlignment(SwingConstants.CENTER);
        itemsTable.getColumnModel().getColumn(0).setCellRenderer(center);
        itemsTable.getColumnModel().getColumn(2).setCellRenderer(plainNumber);
        itemsTable.getColumnModel().getColumn(3).setCellRenderer(center);
        itemsTable.getColumnModel().getColumn(4).setCellRenderer(Ui.moneyRenderer());
        itemsTable.getColumnModel().getColumn(5).setCellRenderer(plainNumber);
        itemsTable.getColumnModel().getColumn(6).setCellRenderer(Ui.moneyRenderer());

        JScrollPane scroll = new JScrollPane(itemsTable);
        scroll.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        card.add(scroll, BorderLayout.CENTER);
        card.add(Ui.muted("Tip: double-click a cell to edit description, quantity, unit, rate or GST."),
                BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildTotalsCard() {
        JPanel card = Ui.card("Summary");
        card.setPreferredSize(new Dimension(330, 0));
        JPanel g = new JPanel(new GridBagLayout());
        g.setOpaque(false);
        int r = 0;
        discount.setPreferredSize(new Dimension(80, discount.getPreferredSize().height));
        totalsRow(g, r++, "Discount %", discount);
        totalsRow(g, r++, "Subtotal", subtotalL);
        totalsRow(g, r++, "Discount", discountL);
        totalsRow(g, r++, "Taxable value", taxableL);
        totalsRow(g, r++, "GST", gstL);
        totalsRow(g, r++, "Round off", roundL);
        separator(g, r++);

        JLabel grandTitle = new JLabel("GRAND TOTAL");
        grandTitle.setFont(grandTitle.getFont().deriveFont(Font.BOLD, 14f));
        grandTitle.setForeground(Ui.NAVY);
        grandL.setFont(grandL.getFont().deriveFont(Font.BOLD, 20f));
        grandL.setForeground(Ui.NAVY);
        totalsRow(g, r++, grandTitle, grandL);
        wordsL.setForeground(Ui.MUTED);
        wordsL.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        wordsL.setLineWrap(true);
        wordsL.setWrapStyleWord(true);
        wordsL.setEditable(false);
        wordsL.setFocusable(false);
        wordsL.setOpaque(false);
        wordsL.setBorder(null);
        GridBagConstraints wc = new GridBagConstraints();
        wc.gridy = r++;
        wc.gridwidth = 2;
        wc.fill = GridBagConstraints.HORIZONTAL;
        wc.insets = new Insets(0, 0, 6, 0);
        g.add(wordsL, wc);
        separator(g, r++);

        GridBagConstraints sc = new GridBagConstraints();
        sc.gridy = r++;
        sc.gridwidth = 2;
        sc.anchor = GridBagConstraints.WEST;
        showSubsidy.setOpaque(false);
        g.add(showSubsidy, sc);
        subsidyL.setForeground(Ui.GREEN);
        totalsRow(g, r++, "Est. subsidy", subsidyL);
        totalsRow(g, r++, "Net after subsidy", netL);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = r;
        filler.weighty = 1;
        g.add(new JLabel(), filler);
        card.add(g, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildTermsCard() {
        JPanel card = Ui.card("Terms & Conditions");
        terms.setLineWrap(true);
        terms.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(terms);
        sp.setPreferredSize(new Dimension(0, 90));
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildActions() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.add(message, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton clear = Ui.button("New / Clear");
        clear.addActionListener(e -> {
            if (!dirty || Ui.confirm(this, "Discard unsaved changes and start a new quotation?")) load(null);
        });
        JButton save = Ui.button("Save");
        save.addActionListener(e -> save());
        JButton pdf = Ui.primary("Save & Open PDF");
        pdf.addActionListener(e -> openPdf());
        JButton print = Ui.button("⎙ Print");
        print.addActionListener(e -> print());
        buttons.add(clear);
        buttons.add(save);
        buttons.add(print);
        buttons.add(pdf);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------
    // Item actions
    // ------------------------------------------------------------------

    private void reloadProducts() {
        Object selected = productCombo.getSelectedItem();
        try {
            List<Product> list = ProductDao.list(null, true);
            productCombo.removeAllItems();
            for (Product p : list) productCombo.addItem(p);
            if (selected instanceof Product sp) {
                for (Product p : list) {
                    if (p.id.equals(sp.id)) productCombo.setSelectedItem(p);
                }
            }
        } catch (Exception e) {
            Ui.error(this, "Could not load products.", e);
        }
    }

    private void addSelectedProduct() {
        Product p = (Product) productCombo.getSelectedItem();
        if (p == null) {
            Ui.warn(this, "Add products in the Products page first.");
            return;
        }
        BigDecimal qty = BigDecimal.valueOf(((Number) addQty.getValue()).doubleValue()).stripTrailingZeros();
        itemsModel.add(QuoteItem.of(p, qty));
        addQty.setValue(1.0);
    }

    private void addCustomLine() {
        QuoteItem it = new QuoteItem();
        it.description = "Custom item";
        itemsModel.add(it);
        int row = itemsModel.getRowCount() - 1;
        itemsTable.setRowSelectionInterval(row, row);
        itemsTable.editCellAt(row, 1);
        Component editor = itemsTable.getEditorComponent();
        if (editor instanceof JTextField tf) {
            tf.selectAll();
            tf.requestFocusInWindow();
        }
    }

    private void autoFillKit() {
        stopEditing();
        if (itemsModel.getRowCount() > 0
                && !Ui.confirm(this, "Replace the current items with a suggested kit?")) {
            return;
        }
        try {
            List<Product> catalog = ProductDao.list(null, true);
            String type = Quotation.SYSTEM_TYPES[systemType.getSelectedIndex()];
            List<QuoteItem> kit = KitBuilder.build(type, capacityKw(), catalog);
            if (kit.isEmpty()) {
                Ui.warn(this, "No suitable products found. Add panels, inverters etc. in the Products page.");
                return;
            }
            itemsModel.setItems(kit);
            message.setForeground(Ui.MUTED);
            message.setText("Kit filled for " + capacityKw().stripTrailingZeros().toPlainString()
                    + " kW – review quantities and prices.");
        } catch (Exception e) {
            Ui.error(this, "Could not build kit.", e);
        }
    }

    private void removeSelected() {
        stopEditing();
        int[] rows = itemsTable.getSelectedRows();
        if (rows.length == 0) return;
        itemsModel.remove(rows);
    }

    private void moveSelected(int delta) {
        stopEditing();
        int row = itemsTable.getSelectedRow();
        int target = row + delta;
        if (row < 0 || target < 0 || target >= itemsModel.getRowCount()) return;
        itemsModel.swap(row, target);
        itemsTable.setRowSelectionInterval(target, target);
    }

    // ------------------------------------------------------------------
    // Save / PDF / print
    // ------------------------------------------------------------------

    /** @return true if saved successfully */
    public boolean save() {
        stopEditing();
        String error = validateForm();
        if (error != null) {
            Ui.warn(this, error);
            return false;
        }
        collect();
        Pricing.compute(current);
        try {
            QuotationDao.save(current);
            dirty = false;
            updateTitle();
            message.setForeground(Ui.GREEN);
            message.setText("✔ Saved " + current.quoteNo + " at "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            return true;
        } catch (Exception e) {
            Ui.error(this, "Could not save the quotation.", e);
            return false;
        }
    }

    private File saveAndGeneratePdf() {
        if ((dirty || current.id == null) && !save()) return null;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            return QuotePdf.generate(current);
        } catch (Exception e) {
            Ui.error(this, "Could not create the PDF.", e);
            return null;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void openPdf() {
        File f = saveAndGeneratePdf();
        if (f == null) return;
        message.setForeground(Ui.GREEN);
        message.setText("✔ PDF saved: " + f.getAbsolutePath());
        openFile(this, f);
    }

    public void print() {
        File f = saveAndGeneratePdf();
        if (f == null) return;
        try {
            if (QuotePrinter.print(f)) {
                message.setForeground(Ui.GREEN);
                message.setText("✔ Sent " + current.quoteNo + " to the printer.");
            }
        } catch (Exception e) {
            Ui.error(this, "Printing failed.", e);
        }
    }

    static void openFile(Component parent, File f) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(f);
            } else {
                Ui.warn(parent, "PDF saved to:\n" + f.getAbsolutePath());
            }
        } catch (Exception e) {
            Ui.error(parent, "PDF saved to " + f.getAbsolutePath() + " but could not be opened.", e);
        }
    }

    // ------------------------------------------------------------------
    // Form <-> model
    // ------------------------------------------------------------------

    private String validateForm() {
        if (name.getText().isBlank()) {
            name.requestFocusInWindow();
            return "Please enter the customer's name.";
        }
        String digits = phone.getText().replaceAll("\\D", "");
        if (!phone.getText().isBlank() && digits.length() < 10) {
            phone.requestFocusInWindow();
            return "Phone number looks too short.";
        }
        String mail = email.getText().trim();
        if (!mail.isEmpty() && !mail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            email.requestFocusInWindow();
            return "Email address doesn't look valid.";
        }
        if (itemsModel.items.isEmpty()) {
            return "Add at least one item (tip: use ⚡ Auto-fill kit).";
        }
        for (QuoteItem it : itemsModel.items) {
            if (it.description.isBlank()) return "Every item needs a description.";
            if (it.qty.signum() <= 0) return "Quantity must be more than zero for \"" + it.description + "\".";
        }
        return null;
    }

    private void collect() {
        current.customerName = name.getText().trim();
        current.customerPhone = phone.getText().trim();
        current.customerEmail = email.getText().trim();
        current.customerAddress = address.getText().trim();
        current.systemType = Quotation.SYSTEM_TYPES[systemType.getSelectedIndex()];
        current.capacityKw = capacityKw();
        current.quoteDate = toLocalDate((Date) date.getValue());
        current.validUntil = current.quoteDate.plusDays((Integer) validDays.getValue());
        current.discountPct = BigDecimal.valueOf(((Number) discount.getValue()).doubleValue());
        current.showSubsidy = showSubsidy.isSelected();
        current.status = (String) status.getSelectedItem();
        current.terms = terms.getText().trim();
        current.items = new ArrayList<>(itemsModel.items);
    }

    /** Updates the summary panel from the current form values. */
    private void recompute() {
        Quotation tmp = new Quotation();
        tmp.items = itemsModel.items;
        tmp.discountPct = BigDecimal.valueOf(((Number) discount.getValue()).doubleValue());
        tmp.systemType = Quotation.SYSTEM_TYPES[systemType.getSelectedIndex()];
        tmp.capacityKw = capacityKw();
        Pricing.compute(tmp);
        subtotalL.setText(Money.inr(tmp.subtotal));
        discountL.setText("- " + Money.inr(tmp.discountAmount));
        taxableL.setText(Money.inr(tmp.subtotal.subtract(tmp.discountAmount)));
        gstL.setText(Money.inr(tmp.gstTotal));
        roundL.setText(Money.fmt(tmp.roundOff));
        grandL.setText(Money.inr(tmp.grandTotal));
        wordsL.setText(Money.words(tmp.grandTotal));
        subsidyL.setText(tmp.subsidy.signum() > 0 ? Money.inr(tmp.subsidy) : "n/a");
        netL.setText(Money.inr(tmp.grandTotal.subtract(tmp.subsidy)));
        showSubsidy.setEnabled(tmp.subsidy.signum() > 0);
    }

    private void updateTitle() {
        if (current.id == null) {
            titleLabel.setText("New Quotation");
            quoteNoLabel.setText("Number is assigned when you save");
        } else {
            titleLabel.setText("Edit Quotation");
            quoteNoLabel.setText(current.quoteNo);
        }
    }

    private void installChangeTracking() {
        DocumentListener doc = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        };
        for (JTextField f : new JTextField[]{name, phone, email, address}) f.getDocument().addDocumentListener(doc);
        terms.getDocument().addDocumentListener(doc);
        for (JSpinner s : new JSpinner[]{capacity, date, validDays, discount}) s.addChangeListener(e -> changed());
        systemType.addActionListener(e -> changed());
        status.addActionListener(e -> changed());
        showSubsidy.addActionListener(e -> changed());
        itemsModel.addTableModelListener(e -> changed());
    }

    private void changed() {
        if (loading) return;
        dirty = true;
        recompute();
    }

    private void stopEditing() {
        if (itemsTable.isEditing()) itemsTable.getCellEditor().stopCellEditing();
    }

    private BigDecimal capacityKw() {
        return BigDecimal.valueOf(((Number) capacity.getValue()).doubleValue());
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static JLabel valueLabel() {
        JLabel l = new JLabel("-");
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    private static void totalsRow(JPanel g, int row, Object label, JComponent value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 8);
        JComponent l = label instanceof JComponent jc ? jc : Ui.muted(label.toString());
        g.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(3, 0, 3, 0);
        g.add(value, c);
    }

    private static void separator(JPanel g, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 6, 0);
        g.add(new JSeparator(), c);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ------------------------------------------------------------------
    // Items table model
    // ------------------------------------------------------------------

    private static class ItemsModel extends AbstractTableModel {
        private static final String[] COLS = {"#", "Description", "Qty", "Unit", "Rate (₹)", "GST %", "Amount (₹)"};
        final List<QuoteItem> items = new ArrayList<>();

        void setItems(List<QuoteItem> list) {
            items.clear();
            for (QuoteItem it : list) items.add(copy(it));
            fireTableDataChanged();
        }

        void add(QuoteItem it) {
            items.add(it);
            fireTableRowsInserted(items.size() - 1, items.size() - 1);
        }

        void remove(int[] rows) {
            for (int i = rows.length - 1; i >= 0; i--) items.remove(rows[i]);
            fireTableDataChanged();
        }

        void swap(int a, int b) {
            QuoteItem tmp = items.get(a);
            items.set(a, items.get(b));
            items.set(b, tmp);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return items.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 0 -> Integer.class;
                case 1, 3 -> String.class;
                default -> BigDecimal.class;
            };
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c >= 1 && c <= 5;
        }

        @Override
        public Object getValueAt(int r, int c) {
            QuoteItem it = items.get(r);
            return switch (c) {
                case 0 -> r + 1;
                case 1 -> it.description;
                case 2 -> it.qty;
                case 3 -> it.unit;
                case 4 -> it.unitPrice;
                case 5 -> it.gstRate;
                default -> it.amount();
            };
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            QuoteItem it = items.get(r);
            switch (c) {
                case 1 -> it.description = String.valueOf(v).trim();
                case 2 -> it.qty = nonNegative(v, it.qty);
                case 3 -> it.unit = String.valueOf(v).trim();
                case 4 -> it.unitPrice = nonNegative(v, it.unitPrice);
                case 5 -> it.gstRate = nonNegative(v, it.gstRate).min(new BigDecimal("100"));
                default -> { }
            }
            fireTableRowsUpdated(r, r);
        }

        private static BigDecimal nonNegative(Object v, BigDecimal fallback) {
            BigDecimal d = v instanceof BigDecimal bd ? bd : Ui.parseDecimal(String.valueOf(v), fallback);
            return d.signum() < 0 ? fallback : d;
        }

        private static QuoteItem copy(QuoteItem s) {
            QuoteItem it = new QuoteItem();
            it.description = s.description;
            it.unit = s.unit;
            it.qty = s.qty;
            it.unitPrice = s.unitPrice;
            it.gstRate = s.gstRate;
            return it;
        }
    }
}
