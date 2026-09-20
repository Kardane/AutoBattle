package dev.kardane.autobattle.jev;

public final class JevRequestException
        extends RuntimeException {
    private final Integer httpStatus;

    public JevRequestException(
        String message,
        Integer httpStatus
    ) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public JevRequestException(
        String message,
        Integer httpStatus,
        Throwable cause
    ) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
