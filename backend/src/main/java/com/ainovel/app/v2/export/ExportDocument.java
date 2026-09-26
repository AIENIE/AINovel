package com.ainovel.app.v2.export;

import java.nio.charset.Charset;

/** Normalized text and metadata shared by format renderers. */
public record ExportDocument(String title, String author, String text, Charset charset) { }
