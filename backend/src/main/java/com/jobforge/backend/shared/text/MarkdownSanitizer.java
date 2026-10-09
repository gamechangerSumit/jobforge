package com.jobforge.backend.shared.text;

import java.util.regex.Pattern;

/** API_CONTRACT §10: Markdown is stored as text with HTML tags stripped server-side. */
public final class MarkdownSanitizer {

    private static final Pattern ACTIVE_BLOCKS = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");
    private static final Pattern TAGS = Pattern.compile("(?s)</?[a-zA-Z!][^>]*>");

    private MarkdownSanitizer() {}

    public static String strip(String markdown) {
        if (markdown == null) {
            return null;
        }
        String withoutBlocks = ACTIVE_BLOCKS.matcher(markdown).replaceAll("");
        return TAGS.matcher(withoutBlocks).replaceAll("").trim();
    }
}
