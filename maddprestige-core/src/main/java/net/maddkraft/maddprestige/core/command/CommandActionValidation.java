package net.maddkraft.maddprestige.core.command;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.api.validation.ValidationReport;

public record CommandActionValidation(List<CommandActionPlan> plans, ValidationReport report) {
    public CommandActionValidation {
        plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
        report = Objects.requireNonNull(report, "report");
        if (report.hasErrors() && !plans.isEmpty()) {
            throw new IllegalArgumentException("Invalid command action validation cannot expose executable plans");
        }
    }
}
