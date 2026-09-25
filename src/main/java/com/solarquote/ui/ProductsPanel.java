package com.solarquote.ui;

import com.solarquote.dao.ProductDao;
import com.solarquote.model.Product;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Product catalog: the default prices and GST rates used when building quotations. */
public class ProductsPanel extends JPanel implements MainFrame.Page {

    private final JTextField search = new JTextField(24);
    private final JCheckBox showInactive = new JCheckBox("Show inactive");
    private final ProductModel model = new ProductModel();
    private final JTable table = new JTable(model);
    private final JLabel footer = Ui.muted(" ");

    public ProductsPanel() {
        setLayout(new BorderLayout());
        setBackground(Ui.BG);

        JButton add = Ui.primary("+ Add Product");
        add.addActionListener(e -> edit(null));

        JPanel card = Ui.card(null);
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText", "Search name, brand or category…");
        search.putClientProperty("JTextField.showClearButton", true);
        showInactive.setOpaque(false);
        left.add(search);
        left.add(showInactive);
        bar.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        JButton editBtn = Ui.button("Edit");
        editBtn.addActionListener(e -> {
            Product p = selected();
            if (p != null) edit(p);
        });
        JButton delete = Ui.danger("Delete");
        delete.addActionListener(e -> deleteSelected());
        right.add(editBtn);
        right.add(delete);
        bar.add(right, BorderLayout.EAST);
        card.add(bar, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.getColumnModel().getColumn(4).setCellRenderer(Ui.moneyRenderer());
        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object v) {
                setText(v == null ? "" : v instanceof BigDecimal bd ? bd.stripTrailingZeros().toPlainString() : v.toString());
            }
        };
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int c : new int[]{3, 5, 6, 7}) table.getColumnModel().getColumn(c).setCellRenderer(center);
        int[] widths = {100, 320, 110, 60, 110, 60, 80, 60};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Product p = selected();
                    if (p != null) edit(p);
                }
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        card.add(sp, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);

        add(Ui.page("Products", add, card), BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { reload(); }
            @Override public void removeUpdate(DocumentEvent e) { reload(); }
            @Override public void changedUpdate(DocumentEvent e) { reload(); }
        });
        showInactive.addActionListener(e -> reload());
    }

    @Override
    public void onShow() {
        reload();
    }

    private void reload() {
        try {
            model.setRows(ProductDao.list(search.getText(), !showInactive.isSelected()));
            footer.setText(model.rows.size() + " product(s)   ·   Prices here are defaults; "
                    + "changing them does not affect saved quotations.");
        } catch (Exception e) {
            Ui.error(this, "Could not load products.", e);
        }
    }

    private Product selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Ui.warn(this, "Select a product first.");
            return null;
        }
        return model.rows.get(table.convertRowIndexToModel(row));
    }

    private void edit(Product p) {
        if (ProductDialog.show(this, p)) reload();
    }

    private void deleteSelected() {
        Product p = selected();
        if (p == null) return;
        if (!Ui.confirm(this, "Delete \"" + p.displayName() + "\"?\n\n"
                + "Saved quotations keep their copy of this item.\n"
                + "Tip: untick \"Active\" instead to hide it without deleting.")) return;
        try {
            ProductDao.delete(p.id);
            reload();
        } catch (Exception e) {
            Ui.error(this, "Could not delete the product.", e);
        }
    }

    // ------------------------------------------------------------------

    private static class ProductModel extends AbstractTableModel {
        private static final String[] COLS = {"Category", "Name", "Brand", "Unit", "Price (₹)", "GST %", "Wattage", "Active"};
        List<Product> rows = new ArrayList<>();

        void setRows(List<Product> list) {
            rows = list;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }

        @Override
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 4, 5 -> BigDecimal.class;
                case 6 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int r, int c) {
            Product p = rows.get(r);
            return switch (c) {
                case 0 -> p.category;
                case 1 -> p.name;
                case 2 -> p.brand;
                case 3 -> p.unit;
                case 4 -> p.price;
                case 5 -> p.gstRate;
                case 6 -> p.wattage;
                default -> p.active ? "Yes" : "No";
            };
        }
    }

    // ------------------------------------------------------------------

    /** Add / edit form for a single product. */
    static class ProductDialog extends JDialog {
        private final JComboBox<String> category = new JComboBox<>(Product.CATEGORIES);
        private final JTextField name = new JTextField(28);
        private final JTextField brand = new JTextField();
        private final JComboBox<String> unit = new JComboBox<>(new String[]{"Nos", "kW", "Set", "Mtr", "Lot"});
        private final JTextField price = new JTextField();
        private final JComboBox<String> gst = new JComboBox<>(new String[]{"5", "12", "18", "28", "0"});
        private final JTextField wattage = new JTextField();
        private final JCheckBox active = new JCheckBox("Active (shown when creating quotations)", true);
        private final Product product;
        private boolean saved;

        private ProductDialog(JComponent parent, Product p) {
            super(SwingUtilities.getWindowAncestor(parent), p == null ? "Add Product" : "Edit Product",
                    ModalityType.APPLICATION_MODAL);
            product = p == null ? new Product() : p;
            unit.setEditable(true);
            gst.setEditable(true);
            wattage.putClientProperty("JTextField.placeholderText", "Panels only, e.g. 540");
            price.putClientProperty("JTextField.placeholderText", "Excluding GST");

            category.setSelectedItem(product.category);
            name.setText(product.name);
            brand.setText(product.brand);
            unit.setSelectedItem(product.unit);
            price.setText(product.price.signum() == 0 && product.id == null ? "" : product.price.toPlainString());
            gst.setSelectedItem(product.gstRate.stripTrailingZeros().toPlainString());
            wattage.setText(product.wattage == null ? "" : product.wattage.toString());
            active.setSelected(product.active);

            Ui.Form form = new Ui.Form()
                    .add("Category", category)
                    .add("Name *", name)
                    .add("Brand", brand)
                    .add("Unit", unit)
                    .add("Price (₹) *", price)
                    .add("GST %", gst)
                    .add("Wattage (Wp)", wattage)
                    .add("", active);

            JButton cancel = Ui.button("Cancel");
            cancel.addActionListener(e -> dispose());
            JButton ok = Ui.primary("Save");
            ok.addActionListener(e -> save());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.add(cancel);
            buttons.add(ok);

            JPanel root = new JPanel(new BorderLayout(0, 14));
            root.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));
            root.add(form, BorderLayout.CENTER);
            root.add(buttons, BorderLayout.SOUTH);
            setContentPane(root);
            getRootPane().setDefaultButton(ok);
            pack();
            setResizable(false);
            setLocationRelativeTo(parent);
        }

        static boolean show(JComponent parent, Product p) {
            ProductDialog d = new ProductDialog(parent, p);
            d.setVisible(true);
            return d.saved;
        }

        private void save() {
            if (name.getText().isBlank()) {
                Ui.warn(this, "Please enter a product name.");
                return;
            }
            BigDecimal pr = Ui.parseDecimal(price.getText(), null);
            if (pr == null || pr.signum() < 0) {
                Ui.warn(this, "Please enter a valid price.");
                return;
            }
            BigDecimal g = Ui.parseDecimal(String.valueOf(gst.getSelectedItem()), null);
            if (g == null || g.signum() < 0 || g.compareTo(new BigDecimal("100")) > 0) {
                Ui.warn(this, "GST must be a percentage between 0 and 100.");
                return;
            }
            Integer w = null;
            if (!wattage.getText().isBlank()) {
                try {
                    w = Integer.parseInt(wattage.getText().trim());
                } catch (NumberFormatException e) {
                    Ui.warn(this, "Wattage must be a whole number.");
                    return;
                }
            }
            String u = String.valueOf(unit.getSelectedItem()).trim();
            if (u.isEmpty() || u.length() > 10) {
                Ui.warn(this, "Unit must be 1–10 characters.");
                return;
            }
            product.category = (String) category.getSelectedItem();
            product.name = name.getText().trim();
            product.brand = brand.getText().trim();
            product.unit = u;
            product.price = pr;
            product.gstRate = g;
            product.wattage = w;
            product.active = active.isSelected();
            try {
                ProductDao.save(product);
                saved = true;
                dispose();
            } catch (Exception e) {
                Ui.error(this, "Could not save the product.", e);
            }
        }
    }
}
