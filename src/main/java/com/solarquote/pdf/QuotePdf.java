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
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders a quotation to an A4 PDF using OpenPDF. */
public final class QuotePdf {

    private static final Color NAVY = new Color(0x1A, 0x2E, 0x4A);
    private static final Color AMBER = new Color(0xF5, 0xA6, 0x23);
    private static final Color LIGHT = new Color(0xF3, 0xF5, 0xF8);
    private static final Color BORDER = new Color(0xD5, 0xDB, 0xE3);
    private static final Color MUTED = new Color(0x5A, 0x67, 0x78);
    private static final Color INK = new Color(0x1F, 0x29, 0x37);
    private static final Color GREEN = new Color(0x1E, 0x7B, 0x4F);

    // Noto Sans is embedded (unlike the built-in Helvetica) so the ₹ sign renders
    private static final BaseFont REGULAR = loadFont("NotoSans-Regular.ttf");
    private static final BaseFont BOLD = loadFont("NotoSans-Bold.ttf");

    private static final Font TITLE = new Font(BOLD, 18, Font.NORMAL, NAVY);
    private static final Font H_WHITE = new Font(BOLD, 8.5f, Font.NORMAL, Color.WHITE);
    private static final Font H_NAVY = new Font(BOLD, 10, Font.NORMAL, NAVY);
    private static final Font BODY = new Font(REGULAR, 8.5f, Font.NORMAL, INK);
    private static final Font BODY_B = new Font(BOLD, 8.5f, Font.NORMAL, INK);
    private static final Font SMALL = new Font(REGULAR, 7.5f, Font.NORMAL, MUTED);
    private static final Font SMALL_INK = new Font(REGULAR, 7.5f, Font.NORMAL, INK);
    private static final Font SMALL_B = new Font(BOLD, 7.5f, Font.NORMAL, NAVY);
    private static final Font LABEL = new Font(BOLD, 7, Font.NORMAL, MUTED);
    private static final Font BIG_W = new Font(BOLD, 11, Font.NORMAL, Color.WHITE);
    private static final Font SUBSIDY = new Font(BOLD, 8.5f, Font.NORMAL, GREEN);

    /** Column split shared by every two-part row, so the right-hand blocks line up. */
    private static final float[] GRID = {58, 2, 40};

    private static final float LOGO_SIZE = 72;
    private static final float GAP = 10;

    private static final Pattern TERM_NO = Pattern.compile("^(\\d+[.)])\\s*(.*)$");

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
            writer.setPageEvent(new Footer(q.quoteNo == null));
            doc.addTitle("Quotation " + (q.quoteNo == null ? "DRAFT" : q.quoteNo));
            doc.addAuthor(Settings.get("company.name"));
            doc.open();

            doc.add(header(q));
            doc.add(customerAndSystem(q));
            doc.add(items(q));
            doc.add(summary(q));
            if (q.showSubsidy && q.subsidy.signum() > 0) {
                doc.add(subsidyBox(q));
            }
            if (!q.terms.isBlank()) {
                doc.add(section("Terms & Conditions"));
                doc.add(terms(q.terms));
            }
            addAtPageBottom(doc, writer, bankAndSignature());
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
        PdfPTable t = gridTable();

        // Left: logo with the company details beside it
        float leftWidth = (PageSize.A4.getWidth() - 72) * GRID[0] / 100;
        Image img = null;
        String logo = Settings.get("company.logo");
        if (!logo.isBlank() && new File(logo).isFile()) {
            try {
                img = Image.getInstance(logo);
                img.scaleToFit(LOGO_SIZE, LOGO_SIZE);
            } catch (Exception ignored) {
                // A broken logo must never stop the quotation from being generated
            }
        }
        float logoCol = img == null ? 0 : LOGO_SIZE + 10;

        PdfPCell details = cell(Rectangle.NO_BORDER);
        details.setVerticalAlignment(Element.ALIGN_MIDDLE);
        details.setPaddingRight(8);
        String name = Settings.get("company.name");
        Paragraph nameP = new Paragraph(name, titleFontFitting(name, leftWidth - logoCol - 8));
        nameP.setLeading(nameP.getFont().getSize() * 1.15f);
        details.addElement(nameP);
        StringBuilder info = new StringBuilder(Settings.get("company.address"));
        appendLine(info, "Phone: ", Settings.get("company.phone"));
        appendLine(info, "Email: ", Settings.get("company.email"));
        appendLine(info, "", Settings.get("company.website"));
        appendLine(info, "GSTIN: ", Settings.get("company.gstin"));
        Paragraph infoP = new Paragraph(info.toString(), SMALL);
        infoP.setLeading(10.5f);
        infoP.setSpacingBefore(2);
        details.addElement(infoP);

        PdfPCell left = cell(Rectangle.NO_BORDER);
        PdfPTable brand = img == null
                ? new PdfPTable(1)
                : new PdfPTable(new float[]{logoCol, leftWidth - logoCol});
        brand.setWidthPercentage(100);
        if (img != null) {
            PdfPCell logoCell = new PdfPCell(img, false);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPadding(0);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            brand.addCell(logoCell);
        }
        brand.addCell(details);
        left.addElement(brand);
        t.addCell(left);
        t.addCell(cell(Rectangle.NO_BORDER));

        // Right: quotation meta box
        PdfPTable meta = new PdfPTable(new float[]{42, 58});
        meta.setWidthPercentage(100);
        PdfPCell title = textCell(spaced("QUOTATION", BIG_W, 2f), Element.ALIGN_CENTER);
        title.setColspan(2);
        title.setBackgroundColor(NAVY);
        title.setBorderColor(NAVY);
        title.setPadding(6);
        meta.addCell(title);
        metaRow(meta, "Quote No", q.quoteNo == null ? "DRAFT" : q.quoteNo);
        metaRow(meta, "Date", q.quoteDate.format(DATE));
        metaRow(meta, "Valid Until", q.validUntil.format(DATE));
        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.addElement(meta);
        t.addCell(right);

        PdfPTable wrapped = wrapWithRule(t);
        wrapped.setSpacingAfter(GAP);
        return wrapped;
    }

    private static PdfPTable customerAndSystem(Quotation q) {
        PdfPTable t = gridTable();

        PdfPCell cust = boxed();
        cust.addElement(eyebrow("QUOTATION FOR"));
        cust.addElement(new Paragraph(q.customerName, H_NAVY));
        StringBuilder sb = new StringBuilder(q.customerAddress);
        appendLine(sb, "Phone: ", q.customerPhone);
        appendLine(sb, "Email: ", q.customerEmail);
        Paragraph addr = new Paragraph(sb.toString(), BODY);
        addr.setLeading(11.5f);
        addr.setSpacingBefore(2);
        cust.addElement(addr);
        t.addCell(cust);
        t.addCell(cell(Rectangle.NO_BORDER));

        PdfPCell sys = boxed();
        sys.addElement(eyebrow("PROPOSED SYSTEM"));
        sys.addElement(new Paragraph(q.capacityKw.stripTrailingZeros().toPlainString() + " kW "
                + Quotation.systemLabel(q.systemType) + " Solar PV System", H_NAVY));
        PdfPTable parts = new PdfPTable(new float[]{28, 72});
        parts.setWidthPercentage(100);
        parts.setSpacingBefore(4);
        addPart(parts, "Panels", findItem(q, "panel", "module"), true);
        addPart(parts, "Inverter", findItem(q, "inverter"), false);
        addPart(parts, "Battery", findItem(q, "battery"), false);
        if (parts.getRows().size() > 0) {
            sys.addElement(parts);
        }
        t.addCell(sys);
        return t;
    }

    private static PdfPTable items(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{5, 43, 8, 8, 13, 8, 15});
        t.setWidthPercentage(100);
        t.setSpacingBefore(GAP);
        t.setHeaderRows(1);   // repeat header on each page
        String[] heads = {"#", "Description", "Qty", "Unit", "Rate", "GST %", "Amount"};
        // Each header sits on the same side as the values beneath it
        int[] aligns = {Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_CENTER, Element.ALIGN_CENTER,
                Element.ALIGN_RIGHT, Element.ALIGN_CENTER, Element.ALIGN_RIGHT};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell c = textCell(new Phrase(heads[i], H_WHITE), aligns[i]);
            c.setBackgroundColor(NAVY);
            c.setBorderColor(NAVY);
            c.setPadding(4.5f);
            c.setPaddingLeft(5);
            c.setPaddingRight(5);
            t.addCell(c);
        }
        int n = 1;
        for (QuoteItem it : q.items) {
            Color bg = n % 2 == 0 ? LIGHT : Color.WHITE;
            String[] values = {String.valueOf(n++), it.description, it.qty.stripTrailingZeros().toPlainString(),
                    it.unit, Money.fmt(it.unitPrice), it.gstRate.stripTrailingZeros().toPlainString(),
                    Money.fmt(it.amount())};
            for (int i = 0; i < values.length; i++) {
                PdfPCell c = textCell(new Phrase(values[i], BODY), aligns[i]);
                c.setBackgroundColor(bg);
                c.setBorderColor(BORDER);
                c.setPadding(3.5f);
                c.setPaddingLeft(5);
                c.setPaddingRight(5);
                t.addCell(c);
            }
        }
        return t;
    }

    /** Amount in words and the GST breakup on the left, the totals on the right. */
    private static PdfPTable summary(Quotation q) {
        PdfPTable outer = gridTable();
        outer.setSpacingBefore(GAP);

        PdfPCell left = cell(Rectangle.NO_BORDER);
        left.addElement(eyebrow("AMOUNT IN WORDS"));
        Paragraph words = new Paragraph(Money.words(q.grandTotal), BODY_B);
        words.setLeading(12);
        left.addElement(words);
        PdfPTable gst = gstSummary(q);
        gst.setSpacingBefore(10);
        left.addElement(gst);
        outer.addCell(left);
        outer.addCell(cell(Rectangle.NO_BORDER));

        PdfPTable t = new PdfPTable(new float[]{50, 50});
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
        t.setSpacingBefore(GAP);
        PdfPCell c = new PdfPCell();
        c.setBackgroundColor(new Color(0xE8, 0xF5, 0xEE));
        c.setBorderColor(GREEN);
        c.setBorder(Rectangle.LEFT);
        c.setBorderWidthLeft(3);
        c.setPadding(8);
        c.setPaddingLeft(10);
        Paragraph p = new Paragraph();
        p.add(new Chunk("PM Surya Ghar Subsidy (indicative): " + Money.rs(q.subsidy), SUBSIDY));
        p.add(new Chunk("      Effective cost after subsidy: ", BODY));
        p.add(new Chunk(Money.rs(q.grandTotal.subtract(q.subsidy)), BODY_B));
        c.addElement(p);
        Paragraph note = new Paragraph("Subsidy is credited by the Government directly to the customer's bank account "
                + "after installation and inspection; the full amount above is payable to us.", SMALL);
        note.setLeading(10);
        note.setSpacingBefore(2);
        c.addElement(note);
        t.addCell(c);
        return t;
    }

    private static PdfPTable gstSummary(Quotation q) {
        PdfPTable t = new PdfPTable(new float[]{18, 30, 26, 26});
        t.setWidthPercentage(100);
        String[] heads = {"GST Rate", "Taxable Value", "CGST", "SGST"};
        int[] aligns = {Element.ALIGN_CENTER, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT};
        for (int i = 0; i < heads.length; i++) {
            PdfPCell c = textCell(new Phrase(heads[i], SMALL_B), aligns[i]);
            c.setBackgroundColor(LIGHT);
            c.setBorderColor(BORDER);
            c.setPadding(3);
            c.setPaddingRight(5);
            t.addCell(c);
        }
        for (Map.Entry<BigDecimal, BigDecimal[]> e : Pricing.gstBreakup(q).entrySet()) {
            BigDecimal half = e.getValue()[1].divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal other = e.getValue()[1].subtract(half);
            String[] values = {e.getKey().toPlainString() + "%", Money.fmt(e.getValue()[0]),
                    Money.fmt(half), Money.fmt(other)};
            for (int i = 0; i < values.length; i++) {
                PdfPCell c = textCell(new Phrase(values[i], SMALL_INK), aligns[i]);
                c.setBorderColor(BORDER);
                c.setPadding(3);
                c.setPaddingRight(5);
                t.addCell(c);
            }
        }
        return t;
    }

    /** Terms as a list with a hanging indent; lines that start with "1." or "1)" keep their number. */
    private static PdfPTable terms(String text) {
        PdfPTable t = new PdfPTable(new float[]{3.5f, 96.5f});
        t.setWidthPercentage(100);
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            Matcher m = TERM_NO.matcher(line.trim());
            boolean numbered = m.matches();
            PdfPCell no = textCell(new Phrase(numbered ? m.group(1) : "", BODY), Element.ALIGN_LEFT);
            PdfPCell body = textCell(new Phrase(numbered ? m.group(2) : line.trim(), BODY), Element.ALIGN_LEFT);
            for (PdfPCell c : new PdfPCell[]{no, body}) {
                c.setBorder(Rectangle.NO_BORDER);
                c.setPadding(0);
                c.setPaddingBottom(1.5f);
                c.setLeading(0, 1.25f);
                c.setVerticalAlignment(Element.ALIGN_TOP);
                c.setUseAscender(false);
            }
            t.addCell(no);
            t.addCell(body);
        }
        return t;
    }

    private static PdfPTable bankAndSignature() {
        PdfPTable t = gridTable();

        PdfPCell bank = cell(Rectangle.NO_BORDER);
        bank.setVerticalAlignment(Element.ALIGN_BOTTOM);
        if (!Settings.get("bank.account_no").isBlank()) {
            bank.addElement(eyebrow("BANK DETAILS"));
            PdfPTable rows = new PdfPTable(new float[]{22, 78});
            rows.setWidthPercentage(70);
            rows.setHorizontalAlignment(Element.ALIGN_LEFT);
            rows.setSpacingBefore(2);
            bankRow(rows, "Bank", Settings.get("bank.name"));
            bankRow(rows, "A/c Name", Settings.get("bank.account_name"));
            bankRow(rows, "A/c No", Settings.get("bank.account_no"));
            bankRow(rows, "IFSC", Settings.get("bank.ifsc"));
            bank.addElement(rows);
        }
        t.addCell(bank);
        t.addCell(cell(Rectangle.NO_BORDER));

        // Signature block: company name, room to sign, then the caption
        PdfPTable sign = new PdfPTable(1);
        sign.setWidthPercentage(100);
        PdfPCell forCo = textCell(new Phrase("For " + Settings.get("company.name"), BODY_B), Element.ALIGN_CENTER);
        forCo.setBorder(Rectangle.NO_BORDER);
        forCo.setPaddingBottom(26);
        sign.addCell(forCo);
        PdfPCell auth = textCell(new Phrase("Authorised Signatory", SMALL), Element.ALIGN_CENTER);
        auth.setBorder(Rectangle.NO_BORDER);
        auth.setPaddingTop(4);
        sign.addCell(auth);
        PdfPCell right = cell(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);
        right.addElement(sign);
        t.addCell(right);
        return t;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Page footer: centred "Page n of N", plus a faint watermark on unsaved drafts. */
    private static class Footer extends PdfPageEventHelper {
        private static final float SIZE = 7.5f;
        private final boolean draft;
        private PdfTemplate total;

        Footer(boolean draft) {
            this.draft = draft;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (draft) {
                // Drawn over the content (translucent) so table backgrounds can't hide it
                PdfContentByte over = writer.getDirectContent();
                over.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.07f);
                over.setGState(gs);
                over.setColorFill(NAVY);
                over.beginText();
                over.setFontAndSize(BOLD, 120);
                Rectangle page = document.getPageSize();
                over.showTextAligned(Element.ALIGN_CENTER, "DRAFT", page.getWidth() / 2, page.getHeight() / 2, 45);
                over.endText();
                over.restoreState();
            }

            // The total isn't known yet, so it goes into a template filled in on close
            String text = "Page " + writer.getPageNumber() + " of ";
            float textWidth = REGULAR.getWidthPoint(text, SIZE);
            float totalWidth = REGULAR.getWidthPoint(String.valueOf(writer.getPageNumber()), SIZE);
            float x = (document.left() + document.right() - textWidth - totalWidth) / 2;
            float y = document.bottom() - 24;
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();
            cb.setColorFill(MUTED);
            cb.beginText();
            cb.setFontAndSize(REGULAR, SIZE);
            cb.setTextMatrix(x, y);
            cb.showText(text);
            cb.endText();
            cb.restoreState();
            cb.addTemplate(total, x + textWidth, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.setColorFill(MUTED);
            total.beginText();
            total.setFontAndSize(REGULAR, SIZE);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }

    /**
     * Draws the table pinned to the bottom margin of the current page, or of a
     * new page when the content above leaves too little room for it.
     */
    private static void addAtPageBottom(Document doc, PdfWriter writer, PdfPTable t) {
        t.setTotalWidth(doc.right() - doc.left());
        t.setLockedWidth(true);
        float height = t.getTotalHeight();
        if (writer.getVerticalPosition(true) - GAP < doc.bottom() + height) {
            doc.newPage();
        }
        t.writeSelectedRows(0, -1, doc.left(), doc.bottom() + height, writer.getDirectContent());
    }

    private static BaseFont loadFont(String file) {
        try (InputStream in = QuotePdf.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) throw new IOException("missing font resource " + file);
            return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new UncheckedIOException(new IOException("Could not load PDF font " + file, e));
        }
    }

    /** Title font shrunk (down to 11pt) so the company name stays on one line beside the logo. */
    private static Font titleFontFitting(String text, float width) {
        float perPoint = BOLD.getWidthPoint(text, 1);
        float size = perPoint <= 0 ? TITLE.getSize() : Math.min(TITLE.getSize(), width / perPoint);
        return new Font(BOLD, Math.max(11, size), Font.NORMAL, NAVY);
    }

    private static PdfPTable gridTable() {
        PdfPTable t = new PdfPTable(GRID);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPTable wrapWithRule(PdfPTable content) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell c = new PdfPCell(content);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(2.5f);
        c.setBorderColorBottom(AMBER);
        c.setPadding(0);
        c.setPaddingBottom(10);
        t.addCell(c);
        return t;
    }

    /** First line item whose description mentions any of the words, case-insensitively. */
    private static QuoteItem findItem(Quotation q, String... words) {
        for (QuoteItem it : q.items) {
            String d = it.description.toLowerCase();
            for (String w : words) {
                if (d.contains(w)) return it;
            }
        }
        return null;
    }

    private static void addPart(PdfPTable t, String label, QuoteItem it, boolean alwaysShowQty) {
        if (it == null) return;
        String qty = it.qty.stripTrailingZeros().toPlainString();
        String value = (alwaysShowQty || it.qty.compareTo(BigDecimal.ONE) != 0 ? qty + " × " : "") + it.description;
        PdfPCell l = textCell(new Phrase(label, SMALL), Element.ALIGN_LEFT);
        PdfPCell v = textCell(new Phrase(value, SMALL_INK), Element.ALIGN_LEFT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(0);
            c.setPaddingBottom(2);
            c.setLeading(0, 1.25f);
            c.setVerticalAlignment(Element.ALIGN_TOP);
            c.setUseAscender(false);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static void metaRow(PdfPTable t, String label, String value) {
        PdfPCell l = textCell(new Phrase(label, SMALL), Element.ALIGN_LEFT);
        PdfPCell v = textCell(new Phrase(value, BODY_B), Element.ALIGN_LEFT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorderColor(BORDER);
            c.setPadding(4);
            c.setPaddingLeft(7);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static void totalRow(PdfPTable t, String label, String value, boolean grand) {
        PdfPCell l = textCell(new Phrase(label, grand ? BIG_W : BODY), Element.ALIGN_LEFT);
        PdfPCell v = textCell(new Phrase(value, grand ? BIG_W : BODY), Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(BORDER);
            c.setPadding(grand ? 6 : 3.5f);
            if (grand) {
                c.setBackgroundColor(NAVY);
                c.setBorderColor(NAVY);
            }
        }
        l.setPaddingLeft(7);
        v.setPaddingRight(7);
        t.addCell(l);
        t.addCell(v);
    }

    private static void bankRow(PdfPTable t, String label, String value) {
        if (value == null || value.isBlank()) return;
        PdfPCell l = textCell(new Phrase(label, SMALL), Element.ALIGN_LEFT);
        PdfPCell v = textCell(new Phrase(value, BODY), Element.ALIGN_LEFT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPadding(0);
            c.setPaddingBottom(1);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private static Paragraph section(String title) {
        Paragraph p = new Paragraph(title, H_NAVY);
        p.setSpacingBefore(GAP);
        p.setSpacingAfter(4);
        return p;
    }

    /** Small letter-spaced caption above a block, e.g. "QUOTATION FOR". */
    private static Paragraph eyebrow(String text) {
        Paragraph p = new Paragraph(spaced(text, LABEL, 0.8f));
        p.setLeading(9);
        p.setSpacingAfter(2);
        return p;
    }

    private static Phrase spaced(String text, Font font, float spacing) {
        Chunk c = new Chunk(text, font);
        c.setCharacterSpacing(spacing);
        return new Phrase(c);
    }

    /** Text cell centred vertically on the text's real height, so mixed font sizes share a midline. */
    private static PdfPCell textCell(Phrase p, int align) {
        PdfPCell c = new PdfPCell(p);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setUseAscender(true);
        c.setUseDescender(true);
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
        c.setPadding(10);
        c.setPaddingTop(8);
        return c;
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(label).append(value);
        }
    }
}
