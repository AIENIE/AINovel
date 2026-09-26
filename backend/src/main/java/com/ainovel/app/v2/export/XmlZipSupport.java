package com.ainovel.app.v2.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XmlZipSupport {
    private XmlZipSupport() { }

    static String xml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    static void zipText(ZipOutputStream zip, String path, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
