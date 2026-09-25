package com.solarquote.ui;

import com.solarquote.dao.QuotationDao;
import com.solarquote.model.Quotation;
import com.solarquote.service.Money;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** At-a-glance numbers, a 6-month chart and the most recent quotations. */
public class DashboardPanel extends JPanel implements MainFrame.Page {

    private final MainFrame frame;
    private final JLabel monthCount = statValue();
    private final JLabel monthValue = statValue();
    private final JLabel acceptedValue = statValue();
    private final JLabel pending = statValue();
    private final JLabel monthCountSub = Ui.muted(" ");
    private final JLabel acceptedSub = Ui.muted(" ");
    private final JLabel pendingSub = Ui.muted(" ");
    private final JLabel monthValueSub = Ui.muted(" ");
    private final BarChart chart = new BarChart();
    private final RecentModel recent = new RecentModel();
    private final JTable recentTable = new JTable(recent);

    public DashboardPanel(MainFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setBackground(Ui.BG);

        JButton newQuote = Ui.primary("+ New Quotation");
        newQuote.addActionListener(e -> frame.newQuotation());

        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.add(statCard("Quotes this month", monthCount, monthCountSub, Ui.AMBER));
        stats.add(statCard("Quoted value this month", monthValue, monthValueSub, Ui.BLUE));
        stats.add(statCard("Won (accepted)", acceptedValue, acceptedSub, Ui.GREEN));
        stats.add(statCard("Awaiting decision", pending, pendingSub, Ui.MUTED));

        JPanel chartCard = Ui.card("Quoted value – last 6 months");
        chartCard.add(chart, BorderLayout.CENTER);
        chartCard.setPreferredSize(new Dimension(420, 0));

        JPanel recentCard = Ui.card("Recent quotations");
        Ui.styleTable(recentTable);
        recentTable.setAutoCreateRowSorter(false);   // already newest-first
        recentTable.setRowSorter(null);
        recentTable.getColumnModel().getColumn(3).setCellRenderer(Ui.moneyRenderer());
        recentTable.getColumnModel().getColumn(4).setCellRenderer(Ui.statusRenderer());
        recentTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openSelected();
            }
        });
        JScrollPane sp = new JScrollPane(recentTable);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        recentCard.add(sp, BorderLayout.CENTER);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        links.setOpaque(false);
        JButton all = Ui.button("View all history →");
        all.addActionListener(e -> frame.show(MainFrame.HISTORY));
        links.add(all);
        recentCard.add(links, BorderLayout.SOUTH);

        JPanel lower = new JPanel(new BorderLayout(14, 0));
        lower.setOpaque(false);
        lower.add(chartCard, BorderLayout.WEST);
        lower.add(recentCard, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.add(stats, BorderLayout.NORTH);
        content.add(lower, BorderLayout.CENTER);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        actions.setOpaque(false);
        actions.add(Ui.muted(today));
        actions.add(newQuote);
        add(Ui.page("Dashboard", actions, content), BorderLayout.CENTER);
    }

    @Override
    public void onShow() {
        try {
            QuotationDao.Stats s = QuotationDao.stats();
            monthCount.setText(String.valueOf(s.monthQuotes));
            monthCountSub.setText(s.totalQuotes + " quotations in total");
            monthValue.setText(compact(s.monthValue));
            monthValueSub.setText(Money.inr(s.monthValue));
            acceptedValue.setText(String.valueOf(s.accepted));
            int conversion = s.totalQuotes == 0 ? 0 : Math.round(100f * s.accepted / s.totalQuotes);
            acceptedSub.setText(conversion + "% conversion · " + compact(s.acceptedValue));
            pending.setText(String.valueOf(s.pending));
            pendingSub.setText("Draft or sent – follow up!");
            chart.setData(s.monthly);
            recent.setRows(QuotationDao.search(null, null, 10));
        } catch (Exception e) {
            Ui.error(this, "Could not load dashboard figures.", e);
        }
    }

    private void openSelected() {
        int row = recentTable.getSelectedRow();
        if (row < 0) return;
        Quotation header = recent.rows.get(recentTable.convertRowIndexToModel(row));
        try {
            Quotation q = QuotationDao.find(header.id);
            if (q != null) frame.openQuotation(q);
        } catch (Exception e) {
            Ui.error(this, "Could not open the quotation.", e);
        }
    }

    // ------------------------------------------------------------------
    // Stat cards
    // ------------------------------------------------------------------

    private static JLabel statValue() {
        JLabel l = new JLabel("–");
        l.setFont(l.getFont().deriveFont(Font.BOLD, 28f));
        l.setForeground(Ui.NAVY);
        return l;
    }

    private static JComponent statCard(String title, JLabel value, JLabel sub, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Ui.BORDER),
                        BorderFactory.createEmptyBorder(14, 16, 14, 16))));
        JLabel t = Ui.muted(title.toUpperCase());
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        card.add(t, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(sub, BorderLayout.SOUTH);
        return card;
    }

    /** ₹ 12.4 L / ₹ 1.2 Cr / ₹ 85,000 */
    static String compact(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        BigDecimal crore = new BigDecimal("10000000");
        BigDecimal lakh = new BigDecimal("100000");
        if (v.compareTo(crore) >= 0) return "₹ " + v.divide(crore, 2, RoundingMode.HALF_UP) + " Cr";
        if (v.compareTo(lakh) >= 0) return "₹ " + v.divide(lakh, 1, RoundingMode.HALF_UP) + " L";
        return "₹ " + Money.fmt(v.setScale(0, RoundingMode.HALF_UP)).replace(".00", "");
    }

    // ------------------------------------------------------------------
    // Bar chart (plain Java2D, no library)
    // ------------------------------------------------------------------

    private static class BarChart extends JComponent {
        private Map<YearMonth, BigDecimal> data = new LinkedHashMap<>();

        void setData(Map<YearMonth, BigDecimal> d) {
            data = d;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int top = 24;
            int bottom = h - 26;
            int n = Math.max(1, data.size());
            BigDecimal max = data.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            g.setColor(Ui.BORDER);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(0, bottom, w, bottom);

            FontMetrics fm = g.getFontMetrics(getFont().deriveFont(11f));
            g.setFont(getFont().deriveFont(11f));
            int slot = w / n;
            int barW = Math.min(44, (int) (slot * 0.55));
            int i = 0;
            YearMonth current = YearMonth.now();
            for (Map.Entry<YearMonth, BigDecimal> e : data.entrySet()) {
                int cx = i * slot + slot / 2;
                double ratio = max.signum() == 0 ? 0 : e.getValue().doubleValue() / max.doubleValue();
                int barH = (int) Math.round((bottom - top) * ratio);
                g.setColor(e.getKey().equals(current) ? Ui.AMBER : Ui.NAVY);
                g.fillRoundRect(cx - barW / 2, bottom - barH, barW, Math.max(barH, 2), 6, 6);

                if (e.getValue().signum() > 0) {
                    String label = compact(e.getValue()).replace("₹ ", "");
                    g.setColor(Ui.NAVY);
                    g.drawString(label, cx - fm.stringWidth(label) / 2, bottom - barH - 5);
                }
                String month = e.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                g.setColor(Ui.MUTED);
                g.drawString(month, cx - fm.stringWidth(month) / 2, h - 8);
                i++;
            }
            if (max.signum() == 0) {
                String msg = "No quotations yet";
                g.setColor(Ui.MUTED);
                g.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------

    private static class RecentModel extends AbstractTableModel {
        private static final String[] COLS = {"Quote No", "Date", "Customer", "Total (₹)", "Status"};
        private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        List<Quotation> rows = new ArrayList<>();

        void setRows(List<Quotation> r) {
            rows = r;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 3 ? BigDecimal.class : String.class; }

        @Override
        public Object getValueAt(int r, int c) {
            Quotation q = rows.get(r);
            return switch (c) {
                case 0 -> q.quoteNo;
                case 1 -> q.quoteDate.format(D);
                case 2 -> q.customerName;
                case 3 -> q.grandTotal;
                default -> q.status;
            };
        }
    }
}
