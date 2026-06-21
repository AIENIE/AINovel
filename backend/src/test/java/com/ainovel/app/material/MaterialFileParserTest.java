package com.ainovel.app.material;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialFileParserTest {
    @Test
    void supportedFileNamesShouldIncludePlainTextMarkdownPdfAndOfficeDocuments() {
        MaterialFileParser parser = new MaterialFileParser(1024);

        assertTrue(parser.supportsFileName("notes.txt"));
        assertTrue(parser.supportsFileName("outline.MD"));
        assertTrue(parser.supportsFileName("clue.pdf"));
        assertTrue(parser.supportsFileName("archive.doc"));
        assertTrue(parser.supportsFileName("chapter.docx"));
    }

    @Test
    void parseShouldReadMarkdownUploadAsUtf8Text() throws Exception {
        MaterialFileParser parser = new MaterialFileParser(1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "world.md",
                "text/markdown",
                "# 港口设定\n雨夜码头有一枚铜扣。".getBytes(StandardCharsets.UTF_8)
        );

        MaterialFileParser.ParsedMaterialFile parsed = parser.parse(file);

        assertEquals("world.md", parsed.fileName());
        assertEquals("# 港口设定\n雨夜码头有一枚铜扣。", parsed.content());
    }

    @Test
    void parseShouldExtractDocxBodyText() throws Exception {
        MaterialFileParser parser = new MaterialFileParser(4096);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes("陆家码头旧案", "巡夜人留下铜扣。")
        );

        MaterialFileParser.ParsedMaterialFile parsed = parser.parse(file);

        assertTrue(parsed.content().contains("陆家码头旧案"));
        assertTrue(parsed.content().contains("巡夜人留下铜扣。"));
    }

    @Test
    void parseShouldRejectUnsupportedExtension() {
        MaterialFileParser parser = new MaterialFileParser(1024);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "payload.exe",
                "application/octet-stream",
                "x".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> parser.parse(file));

        assertTrue(ex.getReason().contains("不支持的素材文件格式"));
    }

    @Test
    void parseShouldRejectFilesOverLimitBeforeParsing() {
        MaterialFileParser parser = new MaterialFileParser(8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.txt",
                "text/plain",
                "123456789".getBytes(StandardCharsets.UTF_8)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> parser.parse(file));

        assertTrue(ex.getReason().contains("素材文件大小不能超过"));
    }

    private byte[] docxBytes(String title, String body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """).formatted(title, body).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
