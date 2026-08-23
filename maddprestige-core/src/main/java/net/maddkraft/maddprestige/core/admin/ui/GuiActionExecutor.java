package net.maddkraft.maddprestige.core.admin.ui;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

@FunctionalInterface
public interface GuiActionExecutor {
    CompletionStage<MessageReference> execute(PermissionSubject subject, GuiAction action);
}
