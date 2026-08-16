package net.maddkraft.maddprestige.core.manual;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Trusted mutation evidence attested by an opaque owner capability. */
public final class ProgressProvenance {
    private final String ownerIdentity;
    private final String source;
    private final Instant observedAt;
    private final UUID attestation;

    ProgressProvenance(String ownerIdentity, String source, Instant observedAt, UUID attestation) {
        this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "owner identity");
        this.source = Objects.requireNonNull(source, "source");
        this.observedAt = Objects.requireNonNull(observedAt, "observed at");
        this.attestation = Objects.requireNonNull(attestation, "attestation");
        if (source.isEmpty() || source.length() > 128 || source.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Progress provenance source is invalid or too long");
        }
    }

    public String ownerIdentity() {
        return ownerIdentity;
    }

    public String source() {
        return source;
    }

    public Instant observedAt() {
        return observedAt;
    }

    UUID attestation() {
        return attestation;
    }
}
