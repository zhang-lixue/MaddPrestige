package net.maddkraft.maddprestige.core.admin.ui;

import java.util.Objects;

/** Platform-neutral text-input projection whose authority remains in its enclosing GUI session. */
public record GuiTextInput(String initialValue) {
    public GuiTextInput {
        initialValue = Objects.requireNonNull(initialValue, "initial value");
        if (initialValue.isEmpty() || initialValue.codePointCount(0, initialValue.length()) > 64) {
            throw new IllegalArgumentException("GUI text input must contain one to sixty-four characters");
        }
    }
}
