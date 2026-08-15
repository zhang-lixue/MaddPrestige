package net.maddkraft.maddprestige.api.audit;

import java.util.Objects;

public record AuditValue(String value, boolean sensitive) {
    public static final String REDACTED = "<redacted>";

    public AuditValue {
        value = Objects.requireNonNull(value, "value");
    }

    public String render(boolean mayViewSensitive) {
        return sensitive && !mayViewSensitive ? REDACTED : value;
    }
}
