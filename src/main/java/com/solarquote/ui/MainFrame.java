package com.solarquote.ui;

import com.solarquote.dao.SettingsDao;
import com.solarquote.model.Quotation;
import com.solarquote.service.Settings;
import com.solarquote.service.UpdateCheck;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Top-level window: dark sidebar on the left, the active page on the right. */
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
        var icon = MainFrame.class.getResource("/icon.png");
        if (icon != null) setIconImage(new javax.swing.ImageIcon(icon).getImage());
        // Small laptop screens (e.g. 1366x768 at 125% scaling) must not get a window bigger than the screen
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1150, screen.width), Math.min(700, screen.height)));
        setPreferredSize(new Dimension(Math.min(1360, screen.width), Math.min(820, screen.height)));

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
        Ui.onThemeChange(this::restyleSidebar);

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

    /** Checks GitHub off the UI thread and, if a newer release exists, offers to open its download page. */
    public void checkForUpdates() {
        Thread t = new Thread(() -> {
            String newer = UpdateCheck.newerVersion();
            if (newer != null) SwingUtilities.invokeLater(() -> showUpdateNotice(newer));
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    private void showUpdateNotice(String newer) {
        Object[] options = {"Download", "Later"};
        int choice = JOptionPane.showOptionDialog(this,
                "SolarQuote " + newer + " is available (you have " + UpdateCheck.currentVersion() + ").\n\n"
                        + "Download the installer and run it. Your quotations and settings are kept.",
                "Update available", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (choice != 0) return;
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(UpdateCheck.RELEASES_PAGE));
        } catch (Exception e) {
            Ui.info(this, "Open this page in your browser to download the update:\n" + UpdateCheck.RELEASES_PAGE);
        }
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
        Ui.bg(side, () -> Ui.SIDEBAR);
        side.setPreferredSize(new Dimension(224, 0));
        side.setBorder(BorderFactory.createEmptyBorder(24, 0, 18, 0));

        JLabel logo = new JLabel("SolarQuote");
        var iconUrl = MainFrame.class.getResource("/icon.png");
        if (iconUrl != null) {
            logo.setIcon(new javax.swing.ImageIcon(new javax.swing.ImageIcon(iconUrl).getImage()
                    .getScaledInstance(30, 30, java.awt.Image.SCALE_SMOOTH)));
            logo.setIconTextGap(10);
        }
        logo.setFont(logo.getFont().deriveFont(Font.BOLD, 19f));
        logo.setForeground(Color.WHITE);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 20, 4, 10));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(logo);

        Ui.fg(companyLabel, () -> Ui.SIDEBAR_TEXT);
        companyLabel.setFont(companyLabel.getFont().deriveFont(12f));
        companyLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 26, 14));
        companyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshCompany();
        side.add(companyLabel);

        String mod = KeyEvent.getModifiersExText(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        Object[][] items = {
            {DASHBOARD, Icons.Kind.DASHBOARD, "1"},
            {NEW_QUOTE, Icons.Kind.NEW, "N"},
            {HISTORY, Icons.Kind.HISTORY, "H"},
            {PRODUCTS, Icons.Kind.PRODUCTS, "4"},
            {SETTINGS, Icons.Kind.SETTINGS, "5"},
        };
        for (Object[] it : items) {
            JButton b = navButton((String) it[0], (Icons.Kind) it[1]);
            b.setToolTipText("Shortcut: " + mod + "+" + it[2]);
            navButtons.put((String) it[0], b);
            side.add(b);
        }
        side.add(Box.createVerticalGlue());

        themeButton = navButton(null, Icons.Kind.MOON);
        themeButton.setToolTipText("Switch between light and dark mode (" + mod + "+D)");
        themeButton.addActionListener(e -> toggleTheme());
        updateThemeButton();
        side.add(themeButton);

        String version = UpdateCheck.currentVersion();
        JLabel ver = new JLabel(version == null ? "Development build" : "Version " + version);
        ver.setFont(ver.getFont().deriveFont(11f));
        ver.setForeground(new Color(0x7F, 0x8C, 0x9D));
        ver.setBorder(BorderFactory.createEmptyBorder(10, 20, 0, 0));
        ver.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(ver);
        return side;
    }

    private JButton themeButton;

    private void toggleTheme() {
        boolean dark = !Ui.isDark();
        Ui.applyTheme(dark);
        try {
            SettingsDao.saveAll(Map.of("ui.theme", dark ? "dark" : "light"));
            Settings.reload();
        } catch (Exception e) {
            Ui.error(this, "Could not remember your theme choice.", e);
        }
    }

    private void restyleSidebar() {
        navButtons.forEach((n, b) -> styleNav(b, n.equals(active), false));
        updateThemeButton();
        repaint();
    }

    private void updateThemeButton() {
        boolean dark = Ui.isDark();
        themeButton.setText(dark ? "Light mode" : "Dark mode");
        themeButton.setIcon(Icons.of(dark ? Icons.Kind.SUN : Icons.Kind.MOON, 18));
        styleNav(themeButton, false, false);
    }

    /** @param name page to show on click, or null for a plain button (the theme toggle) */
    private JButton navButton(String name, Icons.Kind icon) {
        // Paint the background ourselves: FlatLaf skips it when the content area isn't filled
        JButton b = new JButton(name == null ? "" : name, Icons.of(icon, 18)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                if (Boolean.TRUE.equals(getClientProperty("sq.selected"))) {
                    g.setColor(Ui.AMBER);
                    g.fillRect(0, 6, 4, getHeight() - 12);
                }
                super.paintComponent(g);
            }
        };
        b.setIconTextGap(14);
        b.setFont(b.getFont().deriveFont(14f));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setPreferredSize(new Dimension(224, 44));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 10));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);   // FlatLaf fills opaque buttons with the parent colour, hiding ours
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { styleNav(b, name != null && name.equals(active), true); }
            @Override public void mouseExited(MouseEvent e) { styleNav(b, name != null && name.equals(active), false); }
        });
        if (name != null) {
            b.addActionListener(e -> {
                if (NEW_QUOTE.equals(name) && !NEW_QUOTE.equals(active)) {
                    // Keep an unsaved draft if there is one; otherwise start fresh
                    quotationPanel.startFreshIfClean();
                }
                show(name);
            });
        }
        styleNav(b, false, false);
        return b;
    }

    private static void styleNav(JButton b, boolean selected, boolean hover) {
        b.putClientProperty("sq.selected", selected);
        b.putClientProperty(Icons.COLOR, selected ? Ui.AMBER : null);
        if (selected) {
            b.setBackground(Ui.SIDEBAR_ACTIVE);
            b.setForeground(Color.WHITE);
            b.setFont(b.getFont().deriveFont(Font.BOLD));
        } else {
            b.setBackground(hover ? Ui.SIDEBAR_HOVER : Ui.SIDEBAR);
            b.setForeground(hover ? Color.WHITE : Ui.SIDEBAR_TEXT);
            b.setFont(b.getFont().deriveFont(Font.PLAIN));
        }
        b.repaint();
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
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_D, mod), "theme", this::toggleTheme);
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
