package com.jobforge.backend.platform.ratelimit;

/** Rate classes from ARCHITECTURE section 15. Only the classes wired so far have properties. */
public enum RateLimitClass {
    AUTH, DEFAULT, SEARCH, WRITE_COMMUNITY, AI, UPLOAD
}
