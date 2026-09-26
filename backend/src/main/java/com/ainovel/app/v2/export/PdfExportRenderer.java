package com.ainovel.app.v2.export;

import com.ainovel.app.common.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Unicode PDF output with a bundled, licensed font; never relies on host fonts. */
public final class PdfExportRenderer implements ExportFormatRenderer {
    public static final String FONT_RESOURCE = "/fonts/AINovelSansSC-Regular.ttf";
    public static final String FONT_SHA256 = "e409839f5050f12679e3679632ec6c3239e75d40689d1d3c0cbbd2651190d6d8";
    private static final float MARGIN = 54;
    private static final float FONT_SIZE = 11;
    private static final float LEADING = 18;
    private static final float LINE_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    @Override
    public void render(ExportDocument source, OutputStream output) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle(source.title());
            document.getDocumentInformation().setAuthor(source.author());
            document.getDocumentInformation().setCreator("AINovel");
            PDType0Font font = PDType0Font.load(document, new ByteArrayInputStream(FontBytes.get()), true);
            Map<Integer, Float> widths = new HashMap<>();
            try (PageWriter writer = new PageWriter(document, font)) {
                for (String paragraph : source.text().split("\\R", -1)) {
                    StringBuilder line = new StringBuilder();
                    float width = 0;
                    for (int offset = 0; offset < paragraph.length();) {
                        int codePoint = paragraph.codePointAt(offset);
                        String glyph = new String(Character.toChars(codePoint));
                        float glyphWidth = width(font, codePoint, glyph, widths);
                        if (width + glyphWidth > LINE_WIDTH && !line.isEmpty()) {
                            writer.line(line.toString());
                            line.setLength(0);
                            width = 0;
                        }
                        line.append(glyph);
                        width += glyphWidth;
                        offset += Character.charCount(codePoint);
                    }
                    writer.line(line.toString());
                }
            }
            document.save(output);
        }
    }

    private float width(PDType0Font font, int codePoint, String glyph, Map<Integer, Float> widths) throws IOException {
        Float cached = widths.get(codePoint);
        if (cached != null) return cached;
        try {
            float width = font.getStringWidth(glyph) * FONT_SIZE / 1000;
            widths.put(codePoint, width);
            return width;
        } catch (IllegalArgumentException ex) {
            // Exposing the unsupported code point helps the author choose a supported format;
            // no manuscript text or parser diagnostics are included.
            throw new BusinessException("PDF_UNSUPPORTED_CHARACTER_U+" + Integer.toHexString(codePoint).toUpperCase(java.util.Locale.ROOT));
        }
    }

    private static final class FontBytes {
        private static byte[] value;
        private static synchronized byte[] get() {
            if (value == null) value = read();
            return value;
        }
        private static byte[] read() {
            try (InputStream input = PdfExportRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
                if (input == null) throw new IllegalStateException("PDF_FONT_MISSING");
                byte[] bytes = input.readAllBytes();
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!FONT_SHA256.equals(digest)) throw new IllegalStateException("PDF_FONT_CHECKSUM_MISMATCH");
                return bytes;
            } catch (IOException | NoSuchAlgorithmException ex) {
                throw new IllegalStateException("PDF_FONT_UNAVAILABLE");
            }
        }
    }

    private static final class PageWriter implements AutoCloseable {
        private final PDDocument document;
        private final PDType0Font font;
        private PDPageContentStream stream;
        private float baseline;

        private PageWriter(PDDocument document, PDType0Font font) {
            this.document = document;
            this.font = font;
        }

        private void line(String value) throws IOException {
            if (stream == null || baseline < MARGIN) nextPage();
            stream.showText(value);
            stream.newLineAtOffset(0, -LEADING);
            baseline -= LEADING;
        }

        private void nextPage() throws IOException {
            close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            stream.beginText();
            stream.setFont(font, FONT_SIZE);
            baseline = PDRectangle.A4.getHeight() - MARGIN - FONT_SIZE;
            stream.newLineAtOffset(MARGIN, baseline);
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.endText();
                stream.close();
                stream = null;
            }
        }
    }
}
