package dev.sergeantfuzzy.chestlink.core.data;

import java.util.Objects;

public class SharedAccess {
    private final AccessLevel accessLevel;
    private final Long expiresAt;

    public SharedAccess(AccessLevel accessLevel, Long expiresAt) {
        this.accessLevel = accessLevel;
        this.expiresAt = expiresAt;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(long now) {
        return expiresAt != null && expiresAt > 0 && now >= expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SharedAccess that = (SharedAccess) o;
        return accessLevel == that.accessLevel && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessLevel, expiresAt);
    }
}