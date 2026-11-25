package com.jobforge.backend.notification.app;

/** Per-user notification delivery preferences. */
public record NotificationPreference(String type, boolean inApp, boolean email) {}