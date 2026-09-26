package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.v2.export.ExportDocument;
import com.ainovel.app.v2.export.PdfExportRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportRendererTest {
    @TempDir Path directory;

    @Test
    void chineseAndSupplementaryTextSurviveWrappingPaginationAndEmbeddedFont() throws Exception {
        String text = "小说导出完整性验证\n第一章 邮戳\n" + "林砚收到一封信。English ABC 123，标点：《故事》！\uD840\uDC87\n".repeat(120) + "终章：全文结束。";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new PdfExportRenderer().render(new ExportDocument("中文小说", "作者", text, StandardCharsets.UTF_8), output);
        try (PDDocument pdf = Loader.loadPDF(output.toByteArray())) {
            assertTrue(pdf.getNumberOfPages() >= 3);
            String extracted = new PDFTextStripper().getText(pdf);
            assertEquals(withoutWhitespace(text), withoutWhitespace(extracted));
            for (var page : pdf.getPages()) {
                for (var name : page.getResources().getFontNames()) assertTrue(page.getResources().getFont(name).isEmbedded());
            }
            PDFRenderer renderer = new PDFRenderer(pdf);
            assertTrue(renderer.renderImageWithDPI(0, 96).getWidth() > 500);
            assertTrue(renderer.renderImageWithDPI(pdf.getNumberOfPages() / 2, 96).getHeight() > 700);
            assertTrue(renderer.renderImageWithDPI(pdf.getNumberOfPages() - 1, 96).getHeight() > 700);
        }
    }

    @Test
    void unsupportedGlyphFailsInsteadOfReplacingText() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BusinessException error = assertThrows(BusinessException.class, () -> new PdfExportRenderer().render(
                new ExportDocument("小说", "作者", "不可替换的字符：😀", StandardCharsets.UTF_8), output));
        assertEquals("PDF_UNSUPPORTED_CHARACTER_U+1F600", error.getMessage());
        assertEquals(0, output.size(), "A failed render must not return a usable-looking PDF");
    }

    @Test
    void fontArtifactMatchesPinnedHash() throws Exception {
        try (var input = getClass().getResourceAsStream(PdfExportRenderer.FONT_RESOURCE)) {
            assertNotNull(input);
            assertEquals(PdfExportRenderer.FONT_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
        assertNotNull(getClass().getResource("/fonts/OFL-NotoSansSC.txt"));
    }

    private String withoutWhitespace(String value) { return value.replaceAll("\\s+", ""); }
}
