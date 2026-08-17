package net.maddkraft.maddprestige.core.admin.ui;

import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;

/** Composition hook that must delegate mutations to ConfigurationAdministrationService. */
@FunctionalInterface
public interface GuiMutationExecutor {
    CompletionStage<String> execute(PermissionSubject subject, GuiAction action);
}
