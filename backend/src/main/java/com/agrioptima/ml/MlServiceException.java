package com.agrioptima.ml;

/**
 * The ML service could not be used. {@link #getMessage()} is safe for API clients (no URLs, no upstream text);
 * {@link #logDetail()} is for the server log only.
 */
public class MlServiceException extends RuntimeException {

    public enum Kind {
        /** Connection refused / host unreachable. */
        UNAVAILABLE,
        /** Connect or read timeout. */
        TIMEOUT,
        /** The service answered with an error status. */
        ERROR_RESPONSE,
        /** 2xx, but the body is missing, unparseable or inconsistent with the request. */
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final int upstreamStatus;
    private final String upstreamProblemType;
    private final String logDetail;

    public MlServiceException(Kind kind, String operation, int upstreamStatus, String upstreamProblemType,
                              String logDetail, Throwable cause) {
        super(publicMessage(kind, operation), cause);
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
        this.upstreamProblemType = upstreamProblemType;
        this.logDetail = logDetail;
    }

    private static String publicMessage(Kind kind, String operation) {
        return switch (kind) {
            case UNAVAILABLE -> "The ML service is not reachable (" + operation + "). Try again later.";
            case TIMEOUT -> "The ML service did not answer in time (" + operation + "). Try again later.";
            case ERROR_RESPONSE -> "The ML service could not process the request (" + operation + ").";
            case INVALID_RESPONSE -> "The ML service returned an invalid response (" + operation + ").";
        };
    }

    public Kind kind() {
        return kind;
    }

    /** HTTP status returned by the ML service, or 0 if there was no response. */
    public int upstreamStatus() {
        return upstreamStatus;
    }

    /** Last segment of the ML problem {@code type}, e.g. {@code unsupported-crop}; null if unknown. */
    public String upstreamProblemType() {
        return upstreamProblemType;
    }

    public String logDetail() {
        return logDetail;
    }
}
