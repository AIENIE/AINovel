package com.ainovel.app.material;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class MaterialFileParser {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "pdf", "doc", "docx");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md");
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private final long maxFileSizeBytes;

    public MaterialFileParser(@Value("${app.material.upload.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    public boolean supportsFileName(String fileName) {
        String extension = extensionOf(fileName);
        return !extension.isBlank() && SUPPORTED_EXTENSIONS.contains(extension);
    }

    public ParsedMaterialFile parse(MultipartFile file) throws IOException {
        String fileName = normalizedFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw badRequest("不支持的素材文件格式，请上传 TXT、Markdown、PDF、DOC 或 DOCX 文件");
        }
        if (file.isEmpty()) {
            throw badRequest("素材文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw badRequest("素材文件大小不能超过 " + formatBytes(maxFileSizeBytes));
        }

        byte[] bytes = file.getBytes();
        String content = TEXT_EXTENSIONS.contains(extension)
                ? new String(bytes, StandardCharsets.UTF_8)
                : parseDocument(fileName, file.getContentType(), bytes);
        if (content.strip().isEmpty()) {
            throw badRequest("素材文件未解析到可用文本");
        }
        return new ParsedMaterialFile(fileName, content);
    }

    private String parseDocument(String fileName, String contentType, byte[] bytes) {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        if (contentType != null && !contentType.isBlank()) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        BodyContentHandler handler = new BodyContentHandler(-1);
        try {
            parser.parse(new ByteArrayInputStream(bytes), handler, metadata, new ParseContext());
            return handler.toString();
        } catch (IOException | SAXException | TikaException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "素材文件解析失败，请确认文件内容可读取", ex);
        }
    }

    private String normalizedFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isBlank()) {
            throw badRequest("素材文件名不能为空");
        }
        return normalized;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String formatBytes(long bytes) {
        if (bytes % (1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L)) + "MB";
        }
        if (bytes % 1024L == 0) {
            return (bytes / 1024L) + "KB";
        }
        return bytes + "B";
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record ParsedMaterialFile(String fileName, String content) {
    }
}
