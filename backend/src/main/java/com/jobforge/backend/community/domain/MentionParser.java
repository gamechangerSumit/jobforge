package com.jobforge.backend.community.domain;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts {@code @handle} tokens (handle rule {@code [a-z0-9_]{3,30}}, API_CONTRACT 10). */
public final class MentionParser {
    // A handle must start the text or follow whitespace/punctuation that cannot be part of an e-mail address.
    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_.@])@([A-Za-z0-9_]{3,30})(?![A-Za-z0-9_@])");
    public static final int MAX_MENTIONS = 10;

    private MentionParser() {}

    /** Distinct lower-cased handles in order of appearance, capped at {@link #MAX_MENTIONS}. */
    public static Set<String> handles(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        Matcher m = MENTION.matcher(text);
        while (m.find() && out.size() < MAX_MENTIONS) {
            out.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
