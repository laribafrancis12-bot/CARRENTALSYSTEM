package com.carrentalsystem.carsystem;

import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Window;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Turns the on-screen receipt into something you can print or save as a PDF.
 *
 * The receipt is captured as a sharp (3x) picture, so the paper looks exactly the same
 * on screen, on a printer and in the PDF. No extra libraries are needed: the PDF file is
 * written directly by savePdf().
 */
public final class ReceiptExporter {

    /** 3x gives crisp text when printed or zoomed in the PDF. */
    private static final double SNAPSHOT_SCALE = 3.0;

    // A4 page size in PDF points (1/72 inch) and the white margin around the receipt.
    private static final double PAGE_W = 595.28;
    private static final double PAGE_H = 841.89;
    private static final double MARGIN = 36;

    private ReceiptExporter() {}

    /** Captures the receipt paper as a high resolution image on a white background. */
    public static WritableImage snapshot(Node receiptPaper) {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        params.setTransform(Transform.scale(SNAPSHOT_SCALE, SNAPSHOT_SCALE));
        return receiptPaper.snapshot(params, null);
    }

    // ------------------------------------------------------------------ print

    /**
     * Opens the system print dialog and prints the receipt, scaled to fit the page.
     *
     * @return true if it was printed, false if the user cancelled the dialog
     * @throws IOException if there is no printer available or printing failed
     */
    public static boolean print(WritableImage image, Window owner) throws IOException {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            throw new IOException("No printer was found on this computer.");
        }
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            return false;
        }

        PageLayout layout = job.getJobSettings().getPageLayout();
        double printableW = layout.getPrintableWidth();
        double printableH = layout.getPrintableHeight();

        double ratio = image.getWidth() / image.getHeight();
        double fitWidth = printableW;
        if (fitWidth / ratio > printableH) {
            fitWidth = printableH * ratio;   // very tall receipt: fit by height instead
        }

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(fitWidth);

        if (job.printPage(layout, view)) {
            job.endJob();
            return true;
        }
        job.cancelJob();
        throw new IOException("The printer could not print this receipt.");
    }

    // -------------------------------------------------------------------- pdf

    /** Saves the receipt picture as a one-page A4 PDF. */
    public static void savePdf(WritableImage image, File file) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();

        byte[] bgra = new byte[w * h * 4];
        image.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), bgra, 0, w * 4);

        // Convert BGRA -> plain RGB, blending any transparency onto white.
        byte[] rgb = new byte[w * h * 3];
        for (int i = 0, o = 0; i < bgra.length; i += 4, o += 3) {
            int a = bgra[i + 3] & 0xFF;
            int b = bgra[i] & 0xFF;
            int g = bgra[i + 1] & 0xFF;
            int r = bgra[i + 2] & 0xFF;
            if (a < 255) {
                r = (r * a + 255 * (255 - a)) / 255;
                g = (g * a + 255 * (255 - a)) / 255;
                b = (b * a + 255 * (255 - a)) / 255;
            }
            rgb[o] = (byte) r;
            rgb[o + 1] = (byte) g;
            rgb[o + 2] = (byte) b;
        }

        writePdf(w, h, rgb, file);
    }

    /** Writes a minimal, valid one-page PDF that shows the RGB picture at the top of an A4 page. */
    static void writePdf(int w, int h, byte[] rgb, File file) throws IOException {
        // Lossless compression keeps the text sharp; flat colours compress very well.
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream out = new DeflaterOutputStream(packed, deflater)) {
            out.write(rgb);
        } finally {
            deflater.end();
        }
        byte[] image = packed.toByteArray();

        // Fit the picture inside the page margins, centred horizontally, starting at the top.
        double drawW = PAGE_W - 2 * MARGIN;
        double drawH = drawW * h / w;
        double maxH = PAGE_H - 2 * MARGIN;
        if (drawH > maxH) {
            drawH = maxH;
            drawW = drawH * w / h;
        }
        double x = (PAGE_W - drawW) / 2;
        double y = PAGE_H - MARGIN - drawH;

        String content = String.format(Locale.ROOT, "q %.2f 0 0 %.2f %.2f %.2f cm /Im0 Do Q", drawW, drawH, x, y);
        byte[] contentBytes = content.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        long[] offsets = new long[6];

        write(pdf, "%PDF-1.4\n");

        offsets[1] = pdf.size();
        write(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = pdf.size();
        write(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets[3] = pdf.size();
        write(pdf, String.format(Locale.ROOT,
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %.2f %.2f] "
                        + "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n",
                PAGE_W, PAGE_H));

        offsets[4] = pdf.size();
        write(pdf, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + image.length + " >>\nstream\n");
        pdf.write(image);
        write(pdf, "\nendstream\nendobj\n");

        offsets[5] = pdf.size();
        write(pdf, "5 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n");
        pdf.write(contentBytes);
        write(pdf, "\nendstream\nendobj\n");

        long xrefAt = pdf.size();
        StringBuilder xref = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i]));
        }
        write(pdf, xref.toString());
        write(pdf, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefAt + "\n%%EOF\n");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            pdf.writeTo(fos);
        }
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }
}