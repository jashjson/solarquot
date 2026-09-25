package com.solarquote.ui;

import com.solarquote.model.Quotation;
import com.solarquote.service.Settings;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Top-level window: navy sidebar on the left, the active page on the right. */
public class MainFrame extends JFrame {

    /** Pages reload their data every time they are shown. */
    public interface Page {
        void onShow();
    }

    public static final String DASHBOARD = "Dashboard";
    public static final String NEW_QUOTE = "New Quotation";
    public static final String HISTORY = "History";
    public static final String PRODUCTS = "Products";
    public static final String SETTINGS = "Settings";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final QuotationPanel quotationPanel;
    private final JLabel companyLabel = new JLabel();
    private String active;

    public MainFrame() {
        super("SolarQuote – Solar Quotation Generator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 700));
        setPreferredSize(new Dimension(1360, 820));

        quotationPanel = new QuotationPanel(this);
        addPage(DASHBOARD, new DashboardPanel(this));
        addPage(NEW_QUOTE, quotationPanel);
        addPage(HISTORY, new HistoryPanel(this));
        addPage(PRODUCTS, new ProductsPanel());
        addPage(SETTINGS, new SettingsPanel(this));

        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        installShortcuts();

        show(DASHBOARD);
        pack();
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    public void show(String name) {
        active = name;
        cards.show(content, name);
        navButtons.forEach((n, b) -> styleNav(b, n.equals(name), false));
        pages.get(name).onShow();
    }

    /** Opens an existing quotation (or a copy of one) in the editor. */
    public void openQuotation(Quotation q) {
        quotationPanel.load(q);
        show(NEW_QUOTE);
    }

    public void newQuotation() {
        quotationPanel.load(null);
        show(NEW_QUOTE);
    }

    /** Called after settings are saved so the sidebar shows the new company name. */
    public void refreshCompany() {
        companyLabel.setText("<html>" + escape(Settings.get("company.name")) + "</html>");
    }

    private void addPage(String name, JComponent page) {
        content.add(page, name);
        pages.put(name, (Page) page);
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Ui.NAVY);
        side.setPreferredSize(new Dimension(210, 0));
        side.setBorder(BorderFactory.createEmptyBorder(20, 0, 16, 0));

        JLabel logo = new JLabel("☀ SolarQuote");
        logo.setFont(logo.getFont().deriveFont(Font.BOLD, 20f));
        logo.setForeground(Ui.AMBER);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 20, 2, 10));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(logo);

        companyLabel.setForeground(new Color(0xB8, 0xC4, 0xD4));
        companyLabel.setFont(companyLabel.getFont().deriveFont(12f));
        companyLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 22, 10));
        companyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshCompany();
        side.add(companyLabel);

        String mod = KeyEvent.getModifiersExText(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        String[][] items = {
            {DASHBOARD, "▦", "1"},
            {NEW_QUOTE, "＋", "N"},
            {HISTORY, "☰", "H"},
            {PRODUCTS, "▤", "4"},
            {SETTINGS, "⚙", "5"},
        };
        for (String[] it : items) {
            JButton b = navButton(it[0], it[1]);
            b.setToolTipText("Shortcut: " + mod + "+" + it[2]);
            navButtons.put(it[0], b);
            side.add(b);
        }
        side.add(Box.createVerticalGlue());

        JLabel ver = new JLabel("v1.0");
        ver.setForeground(new Color(0x7F, 0x8C, 0x9D));
        ver.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(ver);
        return side;
    }

    private JButton navButton(String name, String icon) {
        // Paint the background ourselves: FlatLaf skips it when the content area isn't filled
        JButton b = new JButton(icon + "   " + name) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        b.setFont(b.getFont().deriveFont(14f));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setPreferredSize(new Dimension(210, 44));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 10));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);   // FlatLaf fills opaque buttons with the parent colour, hiding ours
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { styleNav(b, name.equals(active), true); }
            @Override public void mouseExited(MouseEvent e) { styleNav(b, name.equals(active), false); }
        });
        b.addActionListener(e -> {
            if (NEW_QUOTE.equals(name) && !NEW_QUOTE.equals(active)) {
                // Keep an unsaved draft if there is one; otherwise start fresh
                quotationPanel.startFreshIfClean();
            }
            show(name);
        });
        styleNav(b, false, false);
        return b;
    }

    private static void styleNav(JButton b, boolean selected, boolean hover) {
        if (selected) {
            b.setBackground(Ui.AMBER);
            b.setForeground(Ui.NAVY);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
        } else {
            b.setBackground(hover ? Ui.NAVY_HOVER : Ui.NAVY);
            b.setForeground(Color.WHITE);
            b.setFont(b.getFont().deriveFont(Font.PLAIN));
        }
    }

    // ------------------------------------------------------------------
    // Keyboard shortcuts (Cmd on macOS, Ctrl elsewhere)
    // ------------------------------------------------------------------

    private void installShortcuts() {
        int mod = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_1, mod), "dash", () -> show(DASHBOARD));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_N, mod), "new", this::newQuotation);
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_H, mod), "history", () -> show(HISTORY));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_4, mod), "products", () -> show(PRODUCTS));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_5, mod), "settings", () -> show(SETTINGS));
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_S, mod), "save", () -> {
            if (NEW_QUOTE.equals(active)) quotationPanel.save();
        });
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_P, mod), "print", () -> {
            if (NEW_QUOTE.equals(active)) quotationPanel.print();
        });
    }

    private void bind(KeyStroke key, String id, Runnable action) {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, id);
        root.getActionMap().put(id, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
