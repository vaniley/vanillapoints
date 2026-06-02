package dev.vaniley.vanillapoints;

final class StorageException extends RuntimeException {
    StorageException(String message) {
        super(message);
    }

    StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
