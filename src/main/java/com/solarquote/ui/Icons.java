package com.solarquote.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Small vector icons drawn in code, so they are crisp at any scale and follow the text colour. */
final class Icons {

    enum Kind { DASHBOARD, NEW, HISTORY, PRODUCTS, SETTINGS, MOON, SUN }

    /** Client property: set a Color on the component to override the icon colour (e.g. amber when selected). */
    static final String COLOR = "sq.iconColor";

    private Icons() {}

    static Icon of(Kind kind, int size) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }

            @Override
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                Object override = c instanceof javax.swing.JComponent jc ? jc.getClientProperty(COLOR) : null;
                g.setColor(override instanceof Color col ? col : c.getForeground());
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(kind, g);
                g.dispose();
            }
        };
    }

    private static void draw(Kind kind, Graphics2D g) {
        switch (kind) {
            case DASHBOARD -> {
                g.fill(new RoundRectangle2D.Double(3, 3, 8, 10, 2, 2));
                g.fill(new RoundRectangle2D.Double(13, 3, 8, 5, 2, 2));
                g.fill(new RoundRectangle2D.Double(13, 10, 8, 11, 2, 2));
                g.fill(new RoundRectangle2D.Double(3, 15, 8, 6, 2, 2));
            }
            case NEW -> {
                g.draw(new RoundRectangle2D.Double(3, 3, 18, 18, 5, 5));
                g.draw(new Line2D.Double(12, 8, 12, 16));
                g.draw(new Line2D.Double(8, 12, 16, 12));
            }
            case HISTORY -> {
                for (int row = 0; row < 3; row++) {
                    double y = 6 + row * 6;
                    g.draw(new Line2D.Double(9, y, 21, y));
                    g.fill(new Ellipse2D.Double(2.5, y - 1.5, 3, 3));
                }
            }
            case PRODUCTS -> {
                Path2D box = new Path2D.Double();
                box.moveTo(12, 2.5);
                box.lineTo(20.5, 7);
                box.lineTo(20.5, 17);
                box.lineTo(12, 21.5);
                box.lineTo(3.5, 17);
                box.lineTo(3.5, 7);
                box.closePath();
                g.draw(box);
                g.draw(new Line2D.Double(3.5, 7, 12, 11.5));
                g.draw(new Line2D.Double(20.5, 7, 12, 11.5));
                g.draw(new Line2D.Double(12, 11.5, 12, 21.5));
            }
            case SETTINGS -> {
                double[] y = {6, 12, 18};
                double[] knob = {8, 16, 10};
                for (int i = 0; i < 3; i++) {
                    g.draw(new Line2D.Double(3, y[i], 21, y[i]));
                    g.fill(new Ellipse2D.Double(knob[i] - 3, y[i] - 3, 6, 6));
                }
            }
            case MOON -> {
                Area moon = new Area(new Ellipse2D.Double(3, 3, 18, 18));
                moon.subtract(new Area(new Ellipse2D.Double(8, 0, 16, 16)));
                g.fill(moon);
            }
            case SUN -> {
                g.fill(new Ellipse2D.Double(7.5, 7.5, 9, 9));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(12 + Math.cos(a) * 7.5, 12 + Math.sin(a) * 7.5,
                                             12 + Math.cos(a) * 10, 12 + Math.sin(a) * 10));
                }
            }
        }
    }
}
