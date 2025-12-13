package dev.sergeantfuzzy.chestlink.update;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class UpdateResult {
    public enum Status {
        UP_TO_DATE,
        OUTDATED,
        AHEAD,
        UNKNOWN
    }

    private final String currentVersion;
    private final String latestVersion;
    private final Status status;
    private final Map<String, String> sources;
    private final Instant checkedAt;

    public UpdateResult(String currentVersion, String latestVersion, Status status, Map<String, String> sources, Instant checkedAt) {
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.status = status;
        this.sources = sources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sources);
        this.checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, String> getSources() {
        return sources;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public String bestUrl() {
        return sources.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    public static UpdateResult unknown(String currentVersion) {
        return new UpdateResult(currentVersion, currentVersion, Status.UNKNOWN, new LinkedHashMap<>(), Instant.now());
    }
}
