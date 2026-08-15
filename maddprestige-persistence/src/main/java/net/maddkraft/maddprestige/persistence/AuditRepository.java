package net.maddkraft.maddprestige.persistence;

import net.maddkraft.maddprestige.api.audit.AuditRecord;

public interface AuditRepository {
    void append(AuditRecord record);
}
