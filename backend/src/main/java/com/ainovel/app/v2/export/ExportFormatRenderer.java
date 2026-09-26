package com.ainovel.app.v2.export;

import java.io.IOException;
import java.io.OutputStream;

public interface ExportFormatRenderer {
    void render(ExportDocument document, OutputStream output) throws IOException;
}
