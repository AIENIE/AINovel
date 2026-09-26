package com.ainovel.app.v2.export;

import java.io.IOException;
import java.io.OutputStream;

public final class TxtExportRenderer implements ExportFormatRenderer {
    @Override
    public void render(ExportDocument document, OutputStream output) throws IOException {
        output.write(document.text().getBytes(document.charset()));
    }
}
