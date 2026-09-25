package com.solarquote.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.solarquote.model.Quotation;
import com.solarquote.model.QuoteItem;
import com.solarquote.service.Money;
import com.solarquote.service.Pricing;
import com.solarquote.service.Settings;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Renders a quotation to an A4 PDF using OpenPDF. */
public final class QuotePdf {

    private static final Color NAVY = new Color(0x1A, 0x2E, 0x4A);
    private static final Color AMBER = new Color(0xF5, 0xA6, 0x23);
    private static final Color LIGHT = new Color(0xF3, 0xF5, 0xF8);
    private static final Color BORDER = new Color(0xD5, 0xDB, 0xE3);
    private static final Color MUTED = new Color(0x5A, 0x67, 0x78);
    private static final Color GREEN = new Color(0x1E, 0x7B, 0x4F);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, NAVY);
    private static final Font H_WHITE = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font H_NAVY = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);
    private static final Font BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    private static final Font BODY_B = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, MUTED);
    private static final Font BIG_W = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private QuotePdf() {}

    /** Writes the PDF into the configured folder and returns the file. */
    public static File generate(Quotation q) throws IOException {
        Path folder = Path.of(Settings.get("pdf.folder").isBlank()
                ? System.getProperty("user.home") : Settings.get("pdf.folder"));
        Files.createDirectories(folder);
        String name = (q.quoteNo == null ? "DRAFT" : q.quoteNo).replaceAll("[^A-Za-z0-9-]", "_");
        File file = folder.resolve(name + ".pdf").toFile();
        write(q, file);
        return file;
    }

    public static void write(Quotation q, File file) throws IOException {
        Document doc = new Document(PageSize.A4, 36, 36, 36, 50);
        FileOutputStream out = new FileOutputStream(file);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer());
            doc.addTitle("Quotation " + q.quoteNo);
            doc.addAuthor(Settings.get("company.name"));
            doc.open();

            doc.add(header(q));
            doc.add(customerAndSystem(q));
            doc.add(items(q));
            doc.add(totals(q));
            if (q.showSubsidy && q.subsidy.signum() > 0) {
                doc.add(subsidyBox(q));
            }
            doc.add(gstSummary(q));
            if (!q.terms.isBlank()) {
                doc.add(section("Terms & Conditions"));
                Paragraph t = new Paragraph(q.terms, BODY);
                t.setLeading(12);
                doc.add(t);
            }
            doc.add(bankAndSignature());
            doc.close();   // flushes the PDF and closes the stream
        } catch (DocumentException e) {
            throw new IOException("Could not create PDF: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) {
                try {
                    doc.close();
                } catch (RuntimeException ignored) {
                    // already failing; keep the original exception
                }
            }
            out.close();
        }
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static PdfPTable header(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{60, 40});
        t.setWidthPercentage(100);

        // Left: logo + company details
        PdfPCell left = cell(Rectangle.NO_BORDER);
        String logo = Settings.get("company.logo");
        if (!logo.isBlank() && new File(logo).isFile()) {
            try {
                Image img = Image.getInstance(logo);
                img.scaleToFit(120, 50);
                left.addElement(img);
            } catch (Exception ignored) {
                // A broken logo must never stop the quotation from being generated
            }
        }
        left.addElement(new Paragraph(Settings.get("company.name"), TITLE));
        StringBuilder info = new StringBuilder(Settings.get("company.address"));
        appendLine(info, "Phone: ", Settings.get("company.phone"));
        appendLine(info, "Email: ", Settings.get("company.email"));
        appendLine(info, "", Settings.get("company.website"));
        appendLine(info, "GSTIN: ", Settings.get("company.gstin"));
        left.addElement(new Paragraph(info.toString(), SMALL));
        t.addCell(left);

        // Right: quotation meta box
        PdfPTable meta = new PdfPTable(new float[]{45, 55});
        meta.setWidthPercentage(100);
        PdfPCell title = new PdfPCell(new Phrase("QUOTATION", BIG_W));
        title.setColspan(2);
        title.setBackgroundColor(NAVY);
        title.setHorizontalAlignment(Element.ALIGN_CENTER);
        title.setPadding(6);
        title.setBorderColor(NAVY);
        meta.addCell(title);
        metaRow(meta, "Quote No", q.quoteNo == null ? "DRAFT" : q.quoteNo);
        metaRow(meta, "Date", q.quoteDate.format(DATE));
        metaRow(meta, "Valid Until", q.validUntil.format(DATE));
        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.addElement(meta);
        t.addCell(right);

        t.setSpacingAfter(6);
        return wrapWithRule(t);
    }

    private static PdfPTable customerAndSystem(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{60, 40});
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);

        PdfPCell cust = boxed();
        cust.addElement(new Paragraph("QUOTATION FOR", SMALL));
        cust.addElement(new Paragraph(q.customerName, H_NAVY));
        StringBuilder sb = new StringBuilder(q.customerAddress);
        appendLine(sb, "Phone: ", q.customerPhone);
        appendLine(sb, "Email: ", q.customerEmail);
        cust.addElement(new Paragraph(sb.toString(), BODY));
        t.addCell(cust);

        PdfPCell sys = boxed();
        sys.addElement(new Paragraph("PROPOSED SYSTEM", SMALL));
        sys.addElement(new Paragraph(q.capacityKw.stripTrailingZeros().toPlainString() + " kW "
                + Quotation.systemLabel(q.systemType) + " Solar PV System", H_NAVY));
        BigDecimal units = q.capacityKw.multiply(new BigDecimal("4")).multiply(new BigDecimal("30"));
        sys.addElement(new Paragraph("Est. generation: ~" + units.setScale(0, java.math.RoundingMode.HALF_UP)
                + " units/month (4 units/kW/day)", SMALL));
        t.addCell(sys);
        return t;
    }

    private static PdfPTable items(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{5, 43, 8, 8, 13, 8, 15});
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        t.setHeaderRows(1);   // repeat header on each page
        for (String h : new String[]{"#", "Description", "Qty", "Unit", "Rate", "GST%", "Amount"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, H_WHITE));
            c.setBackgroundColor(NAVY);
            c.setBorderColor(NAVY);
            c.setPadding(5);
            c.setHorizontalAlignment(h.equals("Description") ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
            t.addCell(c);
        }
        int n = 1;
        for (QuoteItem it : q.items) {
            Color bg = n % 2 == 0 ? LIGHT : Color.WHITE;
            t.addCell(itemCell(String.valueOf(n++), Element.ALIGN_CENTER, bg));
            t.addCell(itemCell(it.description, Element.ALIGN_LEFT, bg));
            t.addCell(itemCell(it.qty.stripTrailingZeros().toPlainString(), Element.ALIGN_CENTER, bg));
            t.addCell(itemCell(it.unit, Element.ALIGN_CENTER, bg));
            t.addCell(itemCell(Money.fmt(it.unitPrice), Element.ALIGN_RIGHT, bg));
            t.addCell(itemCell(it.gstRate.stripTrailingZeros().toPlainString(), Element.ALIGN_CENTER, bg));
            t.addCell(itemCell(Money.fmt(it.amount()), Element.ALIGN_RIGHT, bg));
        }
        return t;
    }

    private static PdfPTable totals(Quotation q) {
        PdfPTable outer = new PdfPTable(new float[]{52, 48});
        outer.setWidthPercentage(100);
        outer.setSpacingBefore(6);

        PdfPCell words = cell(Rectangle.NO_BORDER);
        words.addElement(new Paragraph("Amount in words", SMALL));
        words.addElement(new Paragraph(Money.words(q.grandTotal), BODY_B));
        outer.addCell(words);

        PdfPTable t = new PdfPTable(new float[]{48, 52});
        t.setWidthPercentage(100);
        totalRow(t, "Subtotal", Money.fmt(q.subtotal), false);
        if (q.discountAmount.signum() > 0) {
            totalRow(t, "Discount (" + q.discountPct.stripTrailingZeros().toPlainString() + "%)",
                    "- " + Money.fmt(q.discountAmount), false);
        }
        totalRow(t, "Taxable Value", Money.fmt(q.subtotal.subtract(q.discountAmount)), false);
        totalRow(t, "GST", Money.fmt(q.gstTotal), false);
        if (q.roundOff.signum() != 0) {
            totalRow(t, "Round Off", Money.fmt(q.roundOff), false);
        }
        totalRow(t, "GRAND TOTAL", Money.rs(q.grandTotal), true);

        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.addElement(t);
        outer.addCell(right);
        return outer;
    }

    private static PdfPTable subsidyBox(Quotation q) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(8);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(0xE8, 0xF5, 0xEE));
        c.setBorderColor(GREEN);
        c.setPadding(8);
        Paragraph p = new Paragraph();
        p.add(new Chunk("PM Surya Ghar Subsidy (indicative): ", new Font(Font.HELVETICA, 9, Font.BOLD, GREEN)));
        p.add(new Chunk(Money.rs(q.subsidy), new Font(Font.HELVETICA, 9, Font.BOLD, GREEN)));
        p.add(new Chunk("     Effective cost after subsidy: ", BODY));
        p.add(new Chunk(Money.rs(q.grandTotal.subtract(q.subsidy)), BODY_B));
        c.addElement(p);
        c.addElement(new Paragraph("Subsidy is credited by the Government directly to the customer's bank account "
                + "after installation and inspection; the full amount above is payable to us.", SMALL));
        t.addCell(c);
        return t;
    }

    private static PdfPTable gstSummary(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{20, 30, 25, 25});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(10);
        for (String h : new String[]{"GST Rate", "Taxable Value", "CGST", "SGST"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 8, Font.BOLD, NAVY)));
            c.setBackgroundColor(LIGHT);
            c.setBorderColor(BORDER);
            c.setPadding(3);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(c);
        }
        for (Map.Entry<BigDecimal, BigDecimal[]> e : Pricing.gstBreakup(q).entrySet()) {
            BigDecimal half = e.getValue()[1].divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal other = e.getValue()[1].subtract(half);
            t.addCell(small(e.getKey().toPlainString() + "%", Element.ALIGN_CENTER));
            t.addCell(small(Money.fmt(e.getValue()[0]), Element.ALIGN_RIGHT));
            t.addCell(small(Money.fmt(half), Element.ALIGN_RIGHT));
            t.addCell(small(Money.fmt(other), Element.ALIGN_RIGHT));
        }
        return t;
    }

    private static PdfPTable bankAndSignature() {
        PdfPTable t = new PdfPTable(new float[]{55, 45});
        t.setWidthPercentage(100);
        t.setSpacingBefore(14);
        t.setKeepTogether(true);

        PdfPCell bank = cell(Rectangle.NO_BORDER);
        if (!Settings.get("bank.account_no").isBlank()) {
            bank.addElement(new Paragraph("Bank Details", H_NAVY));
            StringBuilder sb = new StringBuilder();
            appendLine(sb, "Bank: ", Settings.get("bank.name"));
            appendLine(sb, "A/c Name: ", Settings.get("bank.account_name"));
            appendLine(sb, "A/c No: ", Settings.get("bank.account_no"));
            appendLine(sb, "IFSC: ", Settings.get("bank.ifsc"));
            bank.addElement(new Paragraph(sb.toString().trim(), BODY));
        }
        t.addCell(bank);

        PdfPCell sign = cell(Rectangle.NO_BORDER);
        Paragraph forCo = new Paragraph("For " + Settings.get("company.name"), BODY_B);
        forCo.setAlignment(Element.ALIGN_RIGHT);
        sign.addElement(forCo);
        Paragraph space = new Paragraph(" ");
        space.setSpacingBefore(28);
        sign.addElement(space);
        Paragraph auth = new Paragraph("Authorised Signatory", BODY);
        auth.setAlignment(Element.ALIGN_RIGHT);
        sign.addElement(auth);
        t.addCell(sign);
        return t;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Page footer: thank-you line and page number. */
    private static class Footer extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            float y = document.bottom() - 20;
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase("Thank you for choosing solar energy!", SMALL), document.left(), y, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), SMALL), document.right(), y, 0);
        }
    }

    private static PdfPTable wrapWithRule(PdfPTable content) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(content);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(2.5f);
        c.setBorderColorBottom(AMBER);
        c.setPaddingBottom(6);
        t.addCell(c);
        return t;
    }

    private static void metaRow(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, SMALL));
        PdfPCell v = new PdfPCell(new Phrase(value, BODY_B));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorderColor(BORDER);
            c.setPadding(4);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static void totalRow(PdfPTable t, String label, String value, boolean grand) {
        Font f = grand ? BIG_W : BODY;
        PdfPCell l = new PdfPCell(new Phrase(label, grand ? BIG_W : BODY));
        PdfPCell v = new PdfPCell(new Phrase(value, f));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorderColor(BORDER);
            c.setPadding(grand ? 6 : 4);
            if (grand) {
                c.setBackgroundColor(NAVY);
                c.setBorderColor(NAVY);
            }
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static Paragraph section(String title) {
        Paragraph p = new Paragraph(title, H_NAVY);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private static PdfPCell itemCell(String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, BODY));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBackgroundColor(bg);
        c.setBorderColor(BORDER);
        c.setPadding(5);
        return c;
    }

    private static PdfPCell small(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK)));
        c.setHorizontalAlignment(align);
        c.setBorderColor(BORDER);
        c.setPadding(3);
        return c;
    }

    private static PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPadding(0);
        return c;
    }

    private static PdfPCell boxed() {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(BORDER);
        c.setBackgroundColor(LIGHT);
        c.setPadding(8);
        return c;
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(label).append(value);
        }
    }
}
