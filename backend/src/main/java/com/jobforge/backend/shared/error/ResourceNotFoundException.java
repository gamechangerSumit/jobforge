package com.jobforge.backend.shared.error;

/** Missing OR not visible to the caller (prevents ID probing). Maps to 404. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
