package net.maddkraft.maddprestige.core.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.maddkraft.maddprestige.api.validation.ValidationFinding;
import net.maddkraft.maddprestige.api.validation.ValidationReport;
import net.maddkraft.maddprestige.api.validation.ValidationSeverity;

public final class CommandActionValidator {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9._-]+)}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public ValidationReport validateConfiguration(CommandActionPolicy policy) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        CommandActionPolicy structural = policy.enabled() ? policy : new CommandActionPolicy(true,
                policy.allowedRoots(), policy.blockedRoots(), policy.allowedTokens(), policy.templates(),
                policy.maximumCommands(), policy.maximumCommandLength(), policy.maximumTriggerDepth());
        for (CommandTemplate template : policy.templates().values()) {
            Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String token : template.declaredTokens()) {
                values.put(token, switch (token) {
                    case "player_uuid" -> "00000000-0000-0000-0000-000000000001";
                    case "player_name" -> "Player";
                    default -> "value";
                });
            }
            CommandActionValidation validation = validateAndPlan(structural,
                    List.of(new CommandActionRequest(template.id(), values)), 0);
            findings.addAll(validation.report().findings());
        }
        if (policy.enabled() && policy.templates().isEmpty()) {
            findings.add(error("command.templates.empty", "Enabled command policy has no templates.",
                    "Configure at least one reviewed template or disable the policy."));
        }
        return ValidationReport.of(findings);
    }

    public CommandActionValidation validateAndPlan(
            CommandActionPolicy policy,
            List<CommandActionRequest> requests,
            int triggerDepth) {
        ArrayList<ValidationFinding> findings = new ArrayList<>();
        ArrayList<CommandActionPlan> plans = new ArrayList<>();
        if (!policy.enabled()) {
            findings.add(error("command.disabled", "Command actions are disabled by policy.",
                    "Explicitly enable a reviewed allowlisted policy before using command actions."));
        }
        if (triggerDepth < 0 || triggerDepth > policy.maximumTriggerDepth()) {
            findings.add(error("command.depth", "Command trigger depth exceeds the configured recursion limit.",
                    "Do not invoke command actions recursively."));
        }
        if (requests.size() > policy.maximumCommands()) {
            findings.add(error("command.count", "Operation requests too many command actions.",
                    "Reduce the command count below the configured bound."));
        }
        for (int index = 0; index < requests.size(); index++) {
            CommandActionRequest request = requests.get(index);
            CommandTemplate template = policy.templates().get(request.templateId());
            if (template == null) {
                findings.add(error("command.template.unknown", "Command template is not allowlisted: "
                        + request.templateId(), "Select an explicitly configured template."));
                continue;
            }
            String rendered = render(policy, template, request.tokenValues(), findings);
            if (rendered == null) {
                continue;
            }
            String root = normalizedRoot(rendered);
            if (policy.blockedRoots().contains(root)) {
                findings.add(error("command.root.blocked", "Command root is blocked: " + root,
                        "Use a non-critical native provider or a separately reviewed allowed command."));
            } else if (!policy.allowedRoots().contains(root)) {
                findings.add(error("command.root.not_allowed", "Command root is not allowlisted: " + root,
                        "Add the exact safe root to the allowlist only after review."));
            } else {
                plans.add(new CommandActionPlan("command-" + index, template.id(), root, rendered,
                        "console:" + root + " via template " + template.id(), true));
            }
        }
        ValidationReport report = ValidationReport.of(findings);
        return report.hasErrors() ? new CommandActionValidation(List.of(), report)
                : new CommandActionValidation(plans, report);
    }

    private static String render(
            CommandActionPolicy policy,
            CommandTemplate template,
            Map<String, String> values,
            List<ValidationFinding> findings) {
        String command = template.command();
        if (containsControl(command) || containsChainingDelimiter(command)) {
            findings.add(error("command.control", "Command template contains a control or chaining delimiter.",
                    "Use one printable single-line command."));
            return null;
        }
        Matcher matcher = TOKEN.matcher(command);
        Set<String> encountered = new LinkedHashSet<>();
        StringBuffer rendered = new StringBuffer();
        boolean valid = true;
        while (matcher.find()) {
            String token = matcher.group(1);
            encountered.add(token);
            String value = values.get(token);
            if (!template.declaredTokens().contains(token) || !policy.allowedTokens().contains(token)
                    || value == null || !safeToken(token, value)) {
                findings.add(error("command.token.invalid", "Token is missing, undeclared, disallowed, or unsafe: "
                        + token, "Supply only structured values for explicitly allowed tokens."));
                valid = false;
                value = "";
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        if (!encountered.equals(template.declaredTokens()) || !values.keySet().equals(encountered)) {
            findings.add(error("command.token.mismatch",
                    "Declared, supplied, and referenced command tokens must match exactly.",
                    "Remove unused tokens and declare every placeholder."));
            valid = false;
        }
        String result = rendered.toString();
        if (TOKEN.matcher(result).find() || result.indexOf('{') >= 0 || result.indexOf('}') >= 0) {
            findings.add(error("command.token.unresolved", "Command contains an unresolved token.",
                    "Resolve every token before execution."));
            valid = false;
        }
        if (containsControl(result)) {
            findings.add(error("command.injection", "Rendered command contains a control character.",
                    "Use structured tokens that cannot add commands."));
            valid = false;
        }
        if (result.length() > policy.maximumCommandLength()) {
            findings.add(error("command.length", "Rendered command exceeds the configured length bound.",
                    "Shorten the template or token values."));
            valid = false;
        }
        try {
            normalizedRoot(result);
        } catch (IllegalArgumentException exception) {
            findings.add(error("command.root.invalid", exception.getMessage(),
                    "Use a simple allowlisted console command root."));
            valid = false;
        }
        return valid ? stripLeadingSlash(result).trim() : null;
    }

    private static boolean safeToken(String token, String value) {
        if (containsControl(value) || value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        if ("player_uuid".equals(token)) {
            try {
                return java.util.UUID.fromString(value).toString().equals(value);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        if ("player_name".equals(token)) {
            return PLAYER_NAME.matcher(value).matches();
        }
        return SAFE_TOKEN.matcher(value).matches();
    }

    private static String normalizedRoot(String command) {
        String stripped = stripLeadingSlash(command).trim();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }
        int space = stripped.indexOf(' ');
        String root = (space < 0 ? stripped : stripped.substring(0, space)).toLowerCase(Locale.ROOT);
        int namespace = root.indexOf(':');
        if (namespace >= 0) {
            root = root.substring(namespace + 1);
        }
        if (!root.matches("[a-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Command root is not a safe normalized identifier");
        }
        return root;
    }

    private static String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean containsChainingDelimiter(String value) {
        return value.indexOf(';') >= 0 || value.contains("&&") || value.contains("||");
    }

    private static ValidationFinding error(String code, String explanation, String remediation) {
        return new ValidationFinding(code, ValidationSeverity.ERROR, "rewards.command-actions", explanation,
                "Unsafe command actions fail closed before operation planning.", remediation);
    }
}
