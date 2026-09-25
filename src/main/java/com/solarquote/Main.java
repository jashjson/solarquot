package com.solarquote;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.solarquote.db.Db;
import com.solarquote.db.DbConfig;
import com.solarquote.service.Settings;
import com.solarquote.ui.DbConfigDialog;
import com.solarquote.ui.MainFrame;
import com.solarquote.ui.Ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;
import java.sql.SQLException;
import java.util.Map;

/**
 * SolarQuote – desktop solar quotation generator.
 *
 * Startup: apply look-and-feel → connect to MySQL (asking for details if needed)
 * → create tables → load settings → show the main window.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        setupLookAndFeel();

        DbConfig config = DbConfig.load();
        String error = null;
        try {
            Db.test(config);
        } catch (SQLException e) {
            error = e.getMessage();
        }
        if (error != null) {
            // First run, or MySQL settings changed: ask until it works or the user gives up
            config = DbConfigDialog.show(null, config, java.nio.file.Files.exists(DbConfig.FILE) ? error : null);
            if (config == null) {
                System.exit(0);
                return;
            }
        }
        Db.configure(config);

        try {
            Db.initSchema();
            Settings.reload();
        } catch (SQLException e) {
            Ui.error(null, "Could not prepare the database.", e);
            System.exit(1);
            return;
        }

        try {
            new MainFrame().setVisible(true);
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, "Startup failed: " + e.getMessage(),
                    "SolarQuote", JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    private static void setupLookAndFeel() {
        System.setProperty("apple.awt.application.name", "SolarQuote");
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", "#1A2E4A",
                "Component.arc", "8",
                "Button.arc", "8",
                "TextComponent.arc", "8"));
        FlatLightLaf.setup();
        UIManager.put("Table.alternateRowColor", new java.awt.Color(0xF8, 0xF9, 0xFB));
        UIManager.put("Table.selectionBackground", new java.awt.Color(0xFD, 0xEB, 0xC8));
        UIManager.put("Table.selectionForeground", Ui.NAVY);
        UIManager.put("TabbedPane.selectedBackground", java.awt.Color.WHITE);
        UIManager.put("defaultFont", UIManager.getFont("defaultFont").deriveFont(Font.PLAIN, 13.5f));
    }
}
