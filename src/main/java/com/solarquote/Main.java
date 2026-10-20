package com.solarquote;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.solarquote.db.Db;
import com.solarquote.service.Settings;
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
 * Startup: apply look-and-feel → open the embedded H2 database (created on first run)
 * → load settings → show the main window.
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::start);
    }

    private static void start() {
        redirectLogs();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Something went wrong:\n" + e,
                    "SolarQuote", JOptionPane.ERROR_MESSAGE);
        });
        setupLookAndFeel();

        try {
            Db.open(Db.defaultDataDir());
            Runtime.getRuntime().addShutdownHook(new Thread(Db::close));
            Settings.reload();
        } catch (Db.AlreadyRunningException e) {
            JOptionPane.showMessageDialog(null,
                    "SolarQuote is already open. Look for its window on the taskbar.",
                    "SolarQuote", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
            return;
        } catch (SQLException e) {
            Ui.error(null, "Could not open the SolarQuote database in\n" + Db.dataDir()
                    + "\n\nYour data is not changed. Details are in " + Db.dataDir().resolve("solarquote.log") + ".", e);
            System.exit(1);
            return;
        }

        if ("dark".equals(Settings.get("ui.theme"))) Ui.applyTheme(true);

        try {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.checkForUpdates();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(null, "Startup failed: " + e.getMessage(),
                    "SolarQuote", JOptionPane.ERROR_MESSAGE);
            throw e;
        }
    }

    /** A packaged Windows app has no console, so keep a log next to the data for support. */
    private static void redirectLogs() {
        try {
            java.nio.file.Files.createDirectories(Db.defaultDataDir());
            java.io.PrintStream log = new java.io.PrintStream(new java.io.FileOutputStream(
                    Db.defaultDataDir().resolve("solarquote.log").toFile(), true), true, "UTF-8");
            System.setErr(log);
            System.setOut(log);
        } catch (java.io.IOException e) {
            // logging is optional
        }
    }

    private static void setupLookAndFeel() {
        System.setProperty("apple.awt.application.name", "SolarQuote");
        Ui.applyTheme(false);
    }
}
