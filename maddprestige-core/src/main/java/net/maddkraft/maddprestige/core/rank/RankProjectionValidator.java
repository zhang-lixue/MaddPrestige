package net.maddkraft.maddprestige.core.rank;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

public final class RankProjectionValidator {
    public ValidationReport validate(String stagePath, ProjectionPolicy projection, Optional<String> group,
            ExternalGroupCatalog catalog) {
        Objects.requireNonNull(stagePath, "stage path");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(catalog, "catalog");
        if (projection == ProjectionPolicy.NONE) {
            return ValidationReport.VALID;
        }
        if (group.isEmpty()) {
            return ValidationReport.of(List.of(finding(stagePath, "rank.group.missing_mapping",
                    "Group projection requires an existing external group name.")));
        }
        var existence = catalog.exists(group.orElseThrow());
        if (!existence.isSuccess()) {
            String message = existence.errors().getFirst().message();
            return ValidationReport.of(List.of(finding(stagePath, "rank.group.unavailable", message)));
        }
        if (!existence.value().orElse(false)) {
            return ValidationReport.of(List.of(finding(stagePath, "rank.group.not_found",
                    "Configured external group does not exist: " + group.orElseThrow())));
        }
        return ValidationReport.VALID;
    }

    private static ValidationFinding finding(String path, String code, String explanation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, path, explanation,
                "The affected stage cannot safely project progression membership.",
                "Create the group outside MaddPrestige or choose an existing group; MaddPrestige never creates groups.");
    }
}
