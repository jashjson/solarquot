package com.solarquote.ui;

import com.solarquote.dao.QuotationDao;
import com.solarquote.model.Quotation;
import com.solarquote.pdf.QuotePdf;
import com.solarquote.pdf.QuotePrinter;
import com.solarquote.service.Money;
import com.solarquote.service.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Searchable list of all saved quotations with open / copy / PDF / print / status / delete. */
public class HistoryPanel extends JPanel implements MainFrame.Page {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final MainFrame frame;
    private final JTextField search = new JTextField(24);
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{"All statuses", "DRAFT", "SENT", "ACCEPTED", "REJECTED"});
    private final HistoryModel model = new HistoryModel();
    private final JTable table = new JTable(model);
    private final JLabel footer = Ui.muted(" ");
    private final Timer searchDelay = new Timer(250, e -> reload());

    public HistoryPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(Ui.BG);

        JButton newQuote = Ui.primary("+ New Quotation");
        newQuote.addActionListener(e -> frame.newQuotation());

        JPanel card = Ui.card(null);
        card.add(buildToolbar(), BorderLayout.NORTH);

        Ui.styleTable(table);
        table.getColumnModel().getColumn(6).setCellRenderer(Ui.moneyRenderer());
        table.getColumnModel().getColumn(7).setCellRenderer(Ui.statusRenderer());
        DefaultTableCellRenderer kw = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object v) {
                setText(v instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : "");
            }
        };
        kw.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(5).setCellRenderer(kw);
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object v) {
                setText(v instanceof LocalDate d ? d.format(DATE) : "");
            }
        });
        int[] widths = {170, 90, 200, 120, 80, 60, 120, 90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) openSelected();
            }

            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        card.add(sp, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);

        add(Ui.page("Quotation History", newQuote, card), BorderLayout.CENTER);

        searchDelay.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchDelay.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { searchDelay.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { searchDelay.restart(); }
        });
        statusFilter.addActionListener(e -> reload());
    }

    @Override
    public void onShow() {
        reload();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", "Search quote no, customer or phone…");
        search.putClientProperty("JTextField.showClearButton", true);
        left.add(search);
        left.add(statusFilter);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JButton open = Ui.button("Open");
        open.addActionListener(e -> openSelected());
        JButton copy = Ui.button("Duplicate");
        copy.setToolTipText("Start a new quotation pre-filled from this one");
        copy.addActionListener(e -> duplicateSelected());
        JButton pdf = Ui.button("PDF");
        pdf.addActionListener(e -> pdfSelected(false));
        JButton print = Ui.button("⎙ Print");
        print.addActionListener(e -> pdfSelected(true));
        JButton status = Ui.button("Status ▾");
        status.addActionListener(e -> statusMenu().show(status, 0, status.getHeight()));
        JButton delete = Ui.danger("Delete");
        delete.addActionListener(e -> deleteSelected());
        for (JButton b : new JButton[]{open, copy, pdf, print, status, delete}) right.add(b);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return bar;
    }

    private void reload() {
        String st = statusFilter.getSelectedIndex() == 0 ? null : (String) statusFilter.getSelectedItem();
        try {
            model.setRows(QuotationDao.search(search.getText(), st, 1000));
            BigDecimal total = model.rows.stream().map(q -> q.grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            footer.setText(model.rows.size() + " quotation(s)   ·   Total value " + Money.inr(total)
                    + "   ·   Double-click to open, right-click for more");
        } catch (Exception e) {
            Ui.error(this, "Could not load quotations.", e);
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private Quotation selected() {
        int view = table.getSelectedRow();
        if (view < 0) {
            Ui.warn(this, "Select a quotation first.");
            return null;
        }
        return model.rows.get(table.convertRowIndexToModel(view));
    }

    /** Loads the full quotation (header + items) for the selected row. */
    private Quotation loadSelected() {
        Quotation header = selected();
        if (header == null) return null;
        try {
            Quotation q = QuotationDao.find(header.id);
            if (q == null) Ui.warn(this, "This quotation no longer exists.");
            return q;
        } catch (Exception e) {
            Ui.error(this, "Could not load the quotation.", e);
            return null;
        }
    }

    private void openSelected() {
        Quotation q = loadSelected();
        if (q != null) frame.openQuotation(q);
    }

    private void duplicateSelected() {
        Quotation q = loadSelected();
        if (q == null) return;
        q.id = null;
        q.quoteNo = null;
        q.status = "DRAFT";
        q.quoteDate = LocalDate.now();
        q.validUntil = q.quoteDate.plusDays(Settings.getInt("quote.validity_days", 30));
        frame.openQuotation(q);
    }

    private void pdfSelected(boolean print) {
        Quotation q = loadSelected();
        if (q == null) return;
        try {
            File f = QuotePdf.generate(q);
            if (print) QuotePrinter.print(f);
            else QuotationPanel.openFile(this, f);
        } catch (Exception e) {
            Ui.error(this, print ? "Printing failed." : "Could not create the PDF.", e);
        }
    }

    private JPopupMenu statusMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (String s : Quotation.STATUSES) {
            JMenuItem item = new JMenuItem("Mark as " + s);
            item.setForeground(Ui.statusColor(s));
            item.addActionListener(e -> {
                Quotation q = selected();
                if (q == null) return;
                try {
                    QuotationDao.updateStatus(q.id, s);
                    reload();
                } catch (Exception ex) {
                    Ui.error(this, "Could not update status.", ex);
                }
            });
            menu.add(item);
        }
        return menu;
    }

    private void deleteSelected() {
        Quotation q = selected();
        if (q == null) return;
        if (!Ui.confirm(this, "Permanently delete quotation " + q.quoteNo + " for " + q.customerName + "?")) return;
        try {
            QuotationDao.delete(q.id);
            reload();
        } catch (Exception e) {
            Ui.error(this, "Could not delete the quotation.", e);
        }
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) return;
        table.setRowSelectionInterval(row, row);
        JPopupMenu menu = new JPopupMenu();
        addItem(menu, "Open", this::openSelected);
        addItem(menu, "Duplicate as new", this::duplicateSelected);
        addItem(menu, "Open PDF", () -> pdfSelected(false));
        addItem(menu, "Print", () -> pdfSelected(true));
        menu.addSeparator();
        for (Object c : statusMenu().getComponents()) menu.add((JMenuItem) c);
        menu.addSeparator();
        addItem(menu, "Delete", this::deleteSelected);
        menu.show(table, e.getX(), e.getY());
    }

    private static void addItem(JPopupMenu menu, String text, Runnable r) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> r.run());
        menu.add(item);
    }

    // ------------------------------------------------------------------
    // Table model
    // ------------------------------------------------------------------

    private static class HistoryModel extends AbstractTableModel {
        private static final String[] COLS = {"Quote No", "Date", "Customer", "Phone", "System", "kW", "Total (₹)", "Status"};
        List<Quotation> rows = new ArrayList<>();

        void setRows(List<Quotation> list) {
            rows = list;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 1 -> LocalDate.class;
                case 5, 6 -> BigDecimal.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int r, int c) {
            Quotation q = rows.get(r);
            return switch (c) {
                case 0 -> q.quoteNo;
                case 1 -> q.quoteDate;
                case 2 -> q.customerName;
                case 3 -> q.customerPhone;
                case 4 -> Quotation.systemLabel(q.systemType);
                case 5 -> q.capacityKw;
                case 6 -> q.grandTotal;
                default -> q.status;
            };
        }
    }
}
