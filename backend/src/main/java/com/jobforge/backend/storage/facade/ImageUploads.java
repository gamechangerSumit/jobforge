package com.jobforge.backend.storage.facade;

import com.jobforge.backend.shared.error.ApiException;
import com.jobforge.backend.shared.error.ErrorCode;

/** Magic-byte image verification (API_CONTRACT 10): the client-declared content type is never trusted. */
public final class ImageUploads {

    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public enum Type {
        PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        Type(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }

        public static Type fromExtension(String key) {
            for (Type t : values()) {
                if (key.endsWith("." + t.extension)) {
                    return t;
                }
            }
            return PNG;
        }
    }

    private ImageUploads() {}

    /** @throws ApiException 413 when too large, 415 when the bytes are not PNG/JPEG/WEBP */
    public static Type verify(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "The file is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, "The image must be at most 2 MB.");
        }
        if (content.length >= 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G'
                && content[4] == 0x0D && content[5] == 0x0A && content[6] == 0x1A && content[7] == 0x0A) {
            return Type.PNG;
        }
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return Type.JPEG;
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return Type.WEBP;
        }
        throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG or WEBP images are accepted.");
    }
}
