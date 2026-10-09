package com.jobforge.backend.profile.app;

/** Port for resume file bytes. Keys are server-generated (never user input). */
public interface ResumeStorage {

    void store(String key, byte[] content);

    byte[] load(String key);

    void delete(String key);
}
