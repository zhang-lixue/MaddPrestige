package net.maddkraft.maddprestige.core.admin.command;

import java.util.List;
import java.util.Objects;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;

public record CommandInvocation(PermissionSubject subject, List<String> arguments) {
    public CommandInvocation {
        subject = Objects.requireNonNull(subject, "subject");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (arguments.size() > 64 || arguments.stream().anyMatch(value -> value.length() > 1024)) {
            throw new IllegalArgumentException("Command input exceeds the bounded administration surface");
        }
    }
}
