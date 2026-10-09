package com.jobforge.backend.job.app;

import java.security.SecureRandom;
import java.util.Locale;

final class Slugs {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Slugs() {}

    /** {@code senior-java-engineer-1a2b3c4d}: readable prefix + 32-bit random suffix; unique index is the guard. */
    static String forTitle(String title) {
        String base = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "job";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100).replaceAll("-+$", "");
        }
        return base + "-" + String.format("%08x", RANDOM.nextInt());
    }
}
