package net.maddkraft.maddprestige.core.admin.ui;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;

/** Composition hook that must delegate mutations to ConfigurationAdministrationService. */
@FunctionalInterface
public interface GuiMutationExecutor {
    CompletionStage<MessageReference> execute(PermissionSubject subject, GuiAction action);
}
