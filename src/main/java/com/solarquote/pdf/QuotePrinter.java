package com.solarquote.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;

/** Sends a generated PDF to a printer via the standard system print dialog. */
public final class QuotePrinter {

    private QuotePrinter() {}

    /** @return true if the job was sent, false if the user cancelled the dialog */
    public static boolean print(File pdf) throws IOException, PrinterException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(pdf.getName());
            job.setPageable(new PDFPageable(doc));
            if (!job.printDialog()) {
                return false;
            }
            job.print();
            return true;
        }
    }
}
