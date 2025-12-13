package dev.sergeantfuzzy.chestlink.core.license;

public final class ActivationResult {
    public enum Status {
        SUCCESS,
        INVALID_FORMAT,
        DECLINED,
        ERROR
    }

    private final boolean success;
    private final Status status;
    private final String message;

    private ActivationResult(boolean success, Status status, String message) {
        this.success = success;
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public static ActivationResult success(String message) {
        return new ActivationResult(true, Status.SUCCESS, message);
    }

    public static ActivationResult invalidFormat(String message) {
        return new ActivationResult(false, Status.INVALID_FORMAT, message);
    }

    public static ActivationResult declined(String message) {
        return new ActivationResult(false, Status.DECLINED, message);
    }

    public static ActivationResult error(String message) {
        return new ActivationResult(false, Status.ERROR, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
