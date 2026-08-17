package net.maddkraft.maddprestige.core.admin;

import java.util.Objects;

public final class AdministrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String remediation;

    public AdministrationException(String code, String message, String remediation) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.remediation = Objects.requireNonNull(remediation, "remediation");
    }

    public String code() {
        return code;
    }

    public String remediation() {
        return remediation;
    }
}
