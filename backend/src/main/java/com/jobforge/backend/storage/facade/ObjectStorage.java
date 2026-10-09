package com.jobforge.backend.storage.facade;

/** Port for binary objects. Keys are server-generated (never user input). */
public interface ObjectStorage {

    void store(String key, byte[] content);

    byte[] load(String key);

    void delete(String key);
}
