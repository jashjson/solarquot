package com.solarquote.ui;

import com.solarquote.service.Money;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

/** Shared colours, component factories and small helpers for a consistent look. */
public final class Ui {

    public static final Color NAVY = new Color(0x1A, 0x2E, 0x4A);
    public static final Color NAVY_HOVER = new Color(0x25, 0x3E, 0x5E);
    public static final Color AMBER = new Color(0xF5, 0xA6, 0x23);
    public static final Color BG = new Color(0xF4, 0xF6, 0xF9);
    public static final Color BORDER = new Color(0xDD, 0xE2, 0xE8);
    public static final Color MUTED = new Color(0x6B, 0x77, 0x85);
    public static final Color GREEN = new Color(0x1E, 0x7B, 0x4F);
    public static final Color RED = new Color(0xC0, 0x39, 0x2B);
    public static final Color BLUE = new Color(0x2D, 0x6C, 0xDF);

    private Ui() {}

    // ------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 22f));
        l.setForeground(NAVY);
        return l;
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setForeground(NAVY);
        return l;
    }

    public static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        return l;
    }

    /** Amber call-to-action button. */
    public static JButton primary(String text) {
        JButton b = new JButton(text);
        b.setBackground(AMBER);
        b.setForeground(NAVY);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.putClientProperty("JButton.buttonType", "roundRect");
        return b;
    }

    public static JButton danger(String text) {
        JButton b = button(text);
        b.setForeground(RED);
        return b;
    }

    /** White rounded panel with padding, used to group related content. */
    public static JPanel card(String heading) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        if (heading != null) p.add(heading(heading), BorderLayout.NORTH);
        return p;
    }

    /** Page wrapper: title (+ optional right-side actions) above the content. */
    public static JPanel page(String title, JComponent actions, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title(title), BorderLayout.WEST);
        if (actions != null) top.add(actions, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    public static void styleTable(JTable t) {
        t.setRowHeight(30);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setGridColor(new Color(0xEE, 0xF0, 0xF3));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setFont(t.getTableHeader().getFont().deriveFont(Font.BOLD));
        t.setAutoCreateRowSorter(true);
    }

    /** Right-aligned Indian-format money renderer. */
    public static DefaultTableCellRenderer moneyRenderer() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(value instanceof BigDecimal bd ? Money.fmt(bd) : String.valueOf(value));
            }
        };
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }

    /** Coloured status text (DRAFT grey, SENT blue, ACCEPTED green, REJECTED red). */
    public static DefaultTableCellRenderer statusRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean sel,
                                                           boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, sel, focus, row, col);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!sel) setForeground(statusColor(String.valueOf(value)));
                return this;
            }
        };
    }

    public static Color statusColor(String status) {
        return switch (status) {
            case "SENT" -> BLUE;
            case "ACCEPTED" -> GREEN;
            case "REJECTED" -> RED;
            default -> MUTED;
        };
    }

    // ------------------------------------------------------------------
    // Forms
    // ------------------------------------------------------------------

    /** Two-column label/field form built on GridBagLayout. */
    public static class Form extends JPanel {
        private int row;

        public Form() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        public Form add(String label, JComponent field) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.insets = new Insets(4, 0, 4, 10);
            c.anchor = GridBagConstraints.NORTHWEST;
            c.gridx = 0;
            JLabel l = new JLabel(label);
            l.setForeground(MUTED);
            add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(4, 0, 4, 0);
            add(field, c);
            return this;
        }

        /** Pushes rows to the top when the form is taller than its content. */
        public Form fillRemaining() {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.weighty = 1;
            add(new JLabel(), c);
            return this;
        }
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    public static void error(Component parent, String what, Exception e) {
        e.printStackTrace();
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        JOptionPane.showMessageDialog(parent, what + "\n\n" + detail, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static BigDecimal parseDecimal(String s, BigDecimal def) {
        try {
            return new BigDecimal(s.trim().replace(",", ""));
        } catch (RuntimeException e) {
            return def;
        }
    }
}
