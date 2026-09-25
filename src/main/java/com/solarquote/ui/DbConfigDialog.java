package com.solarquote.ui;

import com.solarquote.db.Db;
import com.solarquote.db.DbConfig;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.sql.SQLException;

/** Asks for MySQL connection details, tests them and saves them to ~/.solarquote/db.properties. */
public class DbConfigDialog extends JDialog {

    private final JTextField host = new JTextField(20);
    private final JTextField port = new JTextField(6);
    private final JTextField database = new JTextField(20);
    private final JTextField user = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JLabel message = new JLabel(" ");
    private DbConfig result;

    private DbConfigDialog(Window owner, DbConfig current, String error) {
        super(owner, "Database Connection", ModalityType.APPLICATION_MODAL);
        host.setText(current.host);
        port.setText(current.port);
        database.setText(current.database);
        user.setText(current.user);
        password.setText(current.password);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 14, 20));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(Ui.heading("Connect to MySQL"), BorderLayout.NORTH);
        top.add(Ui.muted("<html>The database is created automatically if it doesn't exist.</html>"),
                BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        Ui.Form form = new Ui.Form()
                .add("Host", host)
                .add("Port", port)
                .add("Database", database)
                .add("Username", user)
                .add("Password", password);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(form, BorderLayout.CENTER);
        center.add(message, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        if (error != null) showError(error);

        JButton test = Ui.button("Test");
        JButton cancel = Ui.button("Cancel");
        JButton ok = Ui.primary("Save & Connect");
        test.addActionListener(e -> {
            if (tryConnect() != null) {
                message.setForeground(Ui.GREEN);
                message.setText("Connection successful.");
            }
        });
        ok.addActionListener(e -> {
            DbConfig c = tryConnect();
            if (c == null) return;
            try {
                c.save();
            } catch (IOException ex) {
                Ui.error(this, "Connected, but could not save settings to " + DbConfig.FILE, ex);
            }
            result = c;
            dispose();
        });
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(test);
        buttons.add(cancel);
        buttons.add(ok);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(ok);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog.
     * @return the working, saved configuration, or null if the user cancelled
     */
    public static DbConfig show(Window owner, DbConfig current, String error) {
        DbConfigDialog d = new DbConfigDialog(owner, current, error);
        d.setVisible(true);
        return d.result;
    }

    private DbConfig tryConnect() {
        DbConfig c = new DbConfig();
        c.host = host.getText().trim();
        c.port = port.getText().trim();
        c.database = database.getText().trim();
        c.user = user.getText().trim();
        c.password = new String(password.getPassword());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Db.test(c);
            return c;
        } catch (SQLException ex) {
            showError(ex.getMessage());
            return null;
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private void showError(String text) {
        message.setForeground(Ui.RED);
        message.setText("<html><body style='width:300px'>" + escape(text) + "</body></html>");
        pack();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
