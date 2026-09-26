package com.ainovel.app.v2.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.ainovel.app.v2.export.XmlZipSupport.*;

public final class EpubExportRenderer implements ExportFormatRenderer {
    @Override
    public void render(ExportDocument data, OutputStream output) throws IOException {
        String text = data.text();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry entry = new ZipEntry("mimetype"); entry.setMethod(ZipEntry.STORED); entry.setSize(mime.length);
            CRC32 crc = new CRC32(); crc.update(mime); entry.setCrc(crc.getValue()); zip.putNextEntry(entry); zip.write(mime); zip.closeEntry();
            zipText(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            String body = Arrays.stream(text.split("\\R")).filter(line -> !line.isBlank()).map(line -> "<p>" + xml(line) + "</p>").reduce("", String::concat);
            zipText(zip, "OEBPS/chapter.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + xml(data.title()) + "</title></head><body>" + body + "</body></html>");
            zipText(zip, "OEBPS/nav.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><nav><ol><li><a href=\"chapter.xhtml\">正文</a></li></ol></nav></body></html>");
            zipText(zip, "OEBPS/content.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + xml(data.title()) + "</dc:title><dc:language>zh-CN</dc:language></metadata><manifest><item id=\"chap\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/></manifest><spine><itemref idref=\"chap\"/></spine></package>");
        }
    }

}
