package com.agrioptima.exception;

/**
 * Resource does not exist or is not visible to the caller.
 * Ownership failures deliberately map here (404) so other users' IDs cannot be probed.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
