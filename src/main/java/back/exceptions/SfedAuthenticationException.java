package back.exceptions;

public class SfedAuthenticationException extends RuntimeException {
    private final String errorCode;

    public SfedAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SfedAuthenticationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
} 