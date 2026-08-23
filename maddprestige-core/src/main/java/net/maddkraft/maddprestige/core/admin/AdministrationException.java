package net.maddkraft.maddprestige.core.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.core.authorization.AuthorizationBlocker;

public final class AdministrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;
    private final String remediation;
    private final Map<String, String> facts;
    private final List<AuthorizationBlocker> authorizationBlockers;
    private final Optional<AdministrationSemanticVariant> semanticVariant;

    public AdministrationException(String code, String message, String remediation) {
        this(code, message, remediation, Map.of(), List.of(), Optional.empty());
    }

    public AdministrationException(String code, String message, String remediation, Object... factPairs) {
        this(code, message, remediation, facts(factPairs), List.of(), Optional.empty());
    }

    public AdministrationException(
            String code,
            AdministrationSemanticVariant semanticVariant,
            String message,
            String remediation,
            Object... factPairs) {
        this(code, message, remediation, facts(factPairs), List.of(), Optional.of(semanticVariant));
    }

    private AdministrationException(
            String code,
            String message,
            String remediation,
            Map<String, String> facts,
            List<AuthorizationBlocker> authorizationBlockers,
            Optional<AdministrationSemanticVariant> semanticVariant) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.remediation = Objects.requireNonNull(remediation, "remediation");
        this.facts = Map.copyOf(Objects.requireNonNull(facts, "facts"));
        this.authorizationBlockers = List.copyOf(Objects.requireNonNull(authorizationBlockers,
                "authorization blockers"));
        this.semanticVariant = Objects.requireNonNull(semanticVariant, "semantic variant");
        this.semanticVariant.ifPresent(variant -> {
            if (!variant.diagnosticCode().equals(code)) {
                throw new IllegalArgumentException("Administration semantic variant does not match diagnostic code");
            }
        });
    }

    public static AdministrationException authorizationRejected(
            String operation,
            List<AuthorizationBlocker> blockers) {
        List<AuthorizationBlocker> immutable = List.copyOf(blockers);
        return new AdministrationException("operation.preview.blocked",
                operation + " is blocked: " + String.join("; ", AuthorizationBlocker.diagnostics(immutable)),
                "Use why to inspect canonical blockers and correct them first.",
                Map.of("operation", operation), immutable, Optional.empty());
    }

    public String code() {
        return code;
    }

    public String remediation() {
        return remediation;
    }

    public Map<String, String> facts() {
        return facts;
    }

    public List<AuthorizationBlocker> authorizationBlockers() {
        return authorizationBlockers;
    }

    public Optional<AdministrationSemanticVariant> semanticVariant() {
        return semanticVariant;
    }

    private static Map<String, String> facts(Object... factPairs) {
        if (factPairs.length % 2 != 0) {
            throw new IllegalArgumentException("Administration facts must be name/value pairs");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < factPairs.length; index += 2) {
            result.put(String.valueOf(factPairs[index]), String.valueOf(factPairs[index + 1]));
        }
        return result;
    }
}
