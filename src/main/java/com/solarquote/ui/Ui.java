package com.solarquote.ui;

import com.solarquote.service.Money;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/** Shared colours, component factories and small helpers for a consistent look. */
public final class Ui {

    // Brand colours: the same in both themes
    public static final Color NAVY = new Color(0x1A, 0x2E, 0x4A);
    public static final Color AMBER = new Color(0xF5, 0xA6, 0x23);

    // Theme colours: reassigned by applyTheme(). Read them when painting / via fg() and bg(),
    // never cache them in a final field, or a theme switch will leave stale colours behind.
    public static Color BG;
    public static Color CARD;
    public static Color BORDER;
    public static Color MUTED;
    public static Color TEXT;          // headings and key figures
    public static Color GREEN;
    public static Color RED;
    public static Color BLUE;
    public static Color CHART_BAR;
    public static Color SIDEBAR;
    public static Color SIDEBAR_HOVER;
    public static Color SIDEBAR_ACTIVE;
    public static Color SIDEBAR_TEXT;

    private static boolean dark;
    private static boolean fontsInstalled;

    private static final String FG = "sq.fg";
    private static final String BG_KEY = "sq.bg";

    private Ui() {}

    // ------------------------------------------------------------------
    // Theme
    // ------------------------------------------------------------------

    private static final java.util.List<Runnable> THEME_LISTENERS = new java.util.ArrayList<>();

    /** Called after every theme switch, for components that paint their own colours (e.g. the sidebar). */
    public static void onThemeChange(Runnable r) {
        THEME_LISTENERS.add(r);
    }

    public static boolean isDark() {
        return dark;
    }

    /** Switches palette, look-and-feel and every open window. Safe to call before any window exists. */
    public static void applyTheme(boolean useDark) {
        dark = useDark;
        if (dark) {
            BG = new Color(0x0F, 0x16, 0x20);
            CARD = new Color(0x17, 0x21, 0x31);
            BORDER = new Color(0x2A, 0x37, 0x4A);
            MUTED = new Color(0x93, 0xA1, 0xB3);
            TEXT = new Color(0xE8, 0xED, 0xF4);
            GREEN = new Color(0x3D, 0xBE, 0x85);
            RED = new Color(0xEF, 0x6B, 0x5E);
            BLUE = new Color(0x5B, 0x9B, 0xFF);
            CHART_BAR = new Color(0x4A, 0x6A, 0x96);
            SIDEBAR = new Color(0x0A, 0x10, 0x18);
            SIDEBAR_HOVER = new Color(0x14, 0x1E, 0x2C);
            SIDEBAR_ACTIVE = new Color(0x1C, 0x29, 0x3B);
        } else {
            BG = new Color(0xF4, 0xF6, 0xF9);
            CARD = Color.WHITE;
            BORDER = new Color(0xDD, 0xE2, 0xE8);
            MUTED = new Color(0x6B, 0x77, 0x85);
            TEXT = NAVY;
            GREEN = new Color(0x1E, 0x7B, 0x4F);
            RED = new Color(0xC0, 0x39, 0x2B);
            BLUE = new Color(0x2D, 0x6C, 0xDF);
            CHART_BAR = NAVY;
            SIDEBAR = NAVY;
            SIDEBAR_HOVER = new Color(0x25, 0x3E, 0x5E);
            SIDEBAR_ACTIVE = new Color(0x2E, 0x48, 0x6B);
        }
        SIDEBAR_TEXT = new Color(0xB8, 0xC4, 0xD4);

        if (!fontsInstalled) {
            FlatInterFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
            FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);
            fontsInstalled = true;
        }
        Map<String, String> defaults = new java.util.HashMap<>(Map.of(
                "@accentColor", dark ? "#F5A623" : "#1A2E4A",
                "@background", hex(BG),
                "@componentBackground", hex(CARD),
                "@foreground", dark ? "#E8EDF4" : "#1F2937",
                "Component.arc", "8",
                "Button.arc", "8",
                "TextComponent.arc", "8",
                "ScrollBar.thumbArc", "999",
                "ScrollBar.width", "12"));
        if (dark) {
            defaults.put("@textBackground", "#1F2B3D");
            defaults.put("@buttonBackground", "#243247");
        }
        FlatLaf.setGlobalExtraDefaults(defaults);
        if (dark) FlatDarkLaf.setup(); else com.formdev.flatlaf.FlatLightLaf.setup();

        UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(Font.PLAIN, 13.5f));
        UIManager.put("Table.alternateRowColor", res(dark ? new Color(0x1B, 0x26, 0x38) : new Color(0xF8, 0xF9, 0xFB)));
        UIManager.put("Table.selectionBackground", res(dark ? new Color(0x4A, 0x3B, 0x17) : new Color(0xFD, 0xEB, 0xC8)));
        UIManager.put("Table.selectionForeground", res(dark ? Color.WHITE : NAVY));
        UIManager.put("Table.selectionInactiveBackground", res(dark ? new Color(0x3A, 0x30, 0x14) : new Color(0xFD, 0xEB, 0xC8)));
        UIManager.put("Table.selectionInactiveForeground", res(dark ? Color.WHITE : NAVY));
        UIManager.put("Table.gridColor", res(dark ? new Color(0x24, 0x30, 0x42) : new Color(0xEE, 0xF0, 0xF3)));
        UIManager.put("Table.cellMargins", new Insets(4, 8, 4, 8));
        UIManager.put("TableHeader.background", res(dark ? new Color(0x1B, 0x26, 0x38) : new Color(0xF3, 0xF5, 0xF8)));
        UIManager.put("TableHeader.foreground", res(MUTED));
        UIManager.put("TableHeader.separatorColor", res(UIManager.getColor("TableHeader.background")));
        UIManager.put("TableHeader.bottomSeparatorColor", res(BORDER));
        UIManager.put("TableHeader.cellMargins", new Insets(6, 8, 6, 8));
        UIManager.put("TabbedPane.selectedBackground", res(CARD));
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("Button.margin", new Insets(6, 14, 6, 14));

        FlatLaf.updateUI();
        for (Window w : Window.getWindows()) refresh(w);
        THEME_LISTENERS.forEach(Runnable::run);
    }

    /** Swing only re-themes colours it knows it owns (UIResource); plain colours would stick across a theme switch. */
    private static Color res(Color c) {
        return new javax.swing.plaf.ColorUIResource(c);
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Foreground that follows the theme. */
    public static <T extends JComponent> T fg(T c, Supplier<Color> color) {
        c.putClientProperty(FG, color);
        c.setForeground(color.get());
        return c;
    }

    /** Background that follows the theme. */
    public static <T extends JComponent> T bg(T c, Supplier<Color> color) {
        c.putClientProperty(BG_KEY, color);
        c.setBackground(color.get());
        return c;
    }

    /** Re-applies theme-bound colours below {@code root}. */
    @SuppressWarnings("unchecked")
    private static void refresh(Component root) {
        if (root instanceof JComponent jc) {
            if (jc.getClientProperty(FG) instanceof Supplier<?> s) jc.setForeground(((Supplier<Color>) s).get());
            if (jc.getClientProperty(BG_KEY) instanceof Supplier<?> s) jc.setBackground(((Supplier<Color>) s).get());
        }
        if (root instanceof java.awt.Container c) {
            for (Component child : c.getComponents()) refresh(child);
        }
        root.repaint();
    }

    /** 1px outline in the current border colour. */
    public static Border lineBorder() {
        return new AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(BORDER);
                g.drawRect(x, y, w - 1, h - 1);
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(1, 1, 1, 1);
            }
        };
    }

    /** Rounded white/dark surface with a hairline border and an optional coloured accent edge. */
    public static class Card extends JPanel {
        private static final int ARC = 14;
        private final Supplier<Color> accent;

        Card(LayoutManager layout, Supplier<Color> accent) {
            super(layout);
            this.accent = accent;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g.setColor(CARD);
            g.fill(shape);
            if (accent != null) {
                g.setClip(shape);
                g.setColor(accent.get());
                g.fillRect(0, 0, 4, getHeight());
                g.setClip(null);
            }
            g.setColor(BORDER);
            g.draw(shape);
            g.dispose();
        }
    }

    /** A scroll pane around {@code content} that only scrolls when the window is too short to fit it. */
    public static JComponent verticalScroll(JComponent content) {
        class Holder extends JPanel implements javax.swing.Scrollable {
            Holder() {
                super(new BorderLayout());
                setOpaque(false);
                add(content, BorderLayout.CENTER);
            }

            @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 24; }
            @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return Math.max(r.height - 40, 24); }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                // Stretch to fill a tall window; scroll once the content needs more room than the window has
                return getParent() instanceof javax.swing.JViewport vp && vp.getHeight() >= getPreferredSize().height;
            }
        }
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(new Holder(),
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createEmptyBorder());   // null would let the look-and-feel put its own border back
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        return sp;
    }

    /** Card with a coloured left edge, used for the dashboard figures. */
    public static JPanel accentCard(Supplier<Color> accent) {
        JPanel p = new Card(new BorderLayout(0, 4), accent);
        p.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 18));
        return p;
    }

    // ------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------

    public static JLabel title(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 24f));
        return fg(l, () -> TEXT);
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        return fg(l, () -> TEXT);
    }

    public static JLabel muted(String text) {
        return fg(new JLabel(text), () -> MUTED);
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
        return fg(button(text), () -> RED);
    }

    /** Rounded surface with padding, used to group related content. */
    public static JPanel card(String heading) {
        JPanel p = new Card(new BorderLayout(0, 12), null);
        if (heading != null) p.add(heading(heading), BorderLayout.NORTH);
        return p;
    }

    /** Page wrapper: title (+ optional right-side actions) above the content. */
    public static JPanel page(String title, JComponent actions, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        bg(p, () -> BG);
        p.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title(title), BorderLayout.WEST);
        if (actions != null) top.add(actions, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    public static void styleTable(JTable t) {
        t.setRowHeight(36);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(new java.awt.Dimension(0, 1));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setReorderingAllowed(false);
        // Line headers up with their data: right-aligned money columns get right-aligned headers
        if (t.getTableHeader().getDefaultRenderer() instanceof DefaultTableCellRenderer hr) {
            hr.setHorizontalAlignment(SwingConstants.LEFT);
        }
        t.getTableHeader().setFont(t.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
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
            add(muted(label), c);
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

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "SolarQuote", JOptionPane.INFORMATION_MESSAGE);
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
