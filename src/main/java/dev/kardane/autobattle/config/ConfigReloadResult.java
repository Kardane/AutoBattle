package dev.kardane.autobattle.config;

public record ConfigReloadResult(
    boolean success,
    String message
) {
    public static ConfigReloadResult ok(String message) {
        return new ConfigReloadResult(true, message);
    }

    public static ConfigReloadResult failure(String message) {
        return new ConfigReloadResult(false, message);
    }
}
