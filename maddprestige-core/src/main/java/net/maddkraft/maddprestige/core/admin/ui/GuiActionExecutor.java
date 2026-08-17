package net.maddkraft.maddprestige.core.admin.ui;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;

@FunctionalInterface
public interface GuiActionExecutor {
    CompletionStage<String> execute(PermissionSubject subject, GuiAction action);
}
