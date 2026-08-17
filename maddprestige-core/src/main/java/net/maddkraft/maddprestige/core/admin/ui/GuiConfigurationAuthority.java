package net.maddkraft.maddprestige.core.admin.ui;

import java.util.UUID;
import net.maddkraft.maddprestige.core.admin.PermissionSubject;
import net.maddkraft.maddprestige.core.admin.config.ConfigurationApplyKind;

/** Server-owned configuration provenance lookups used while rendering visual authority. */
public interface GuiConfigurationAuthority {
    ConfigurationApplyKind draftKind(PermissionSubject subject, UUID draftId);

    ConfigurationApplyKind acknowledgementKind(PermissionSubject subject, UUID acknowledgementId);
}
