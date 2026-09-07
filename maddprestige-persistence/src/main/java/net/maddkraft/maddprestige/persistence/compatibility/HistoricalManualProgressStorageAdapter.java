package net.maddkraft.maddprestige.persistence.compatibility;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.core.compatibility.LegacyProviderIdentifiers;
import net.maddkraft.maddprestige.core.manual.ManualProgressRecord;
import net.maddkraft.maddprestige.core.manual.ManualProgressRepository;

/** Preserves the historical manual-progress storage namespace behind canonical provider IDs. */
public final class HistoricalManualProgressStorageAdapter implements ManualProgressRepository {
    private final ManualProgressRepository delegate;

    public HistoricalManualProgressStorageAdapter(ManualProgressRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate repository");
    }

    @Override
    public Map<UUID, ManualProgressRecord> load(ProviderId providerId, MetricId metricId) {
        ProviderId canonical = LegacyProviderIdentifiers.canonicalize(providerId);
        ProviderId storageKey = LegacyProviderIdentifiers.historicalStorageKey(canonical);
        LinkedHashMap<UUID, ManualProgressRecord> result = new LinkedHashMap<>();
        delegate.load(storageKey, metricId).forEach((playerId, record) -> result.put(playerId,
                withProvider(record, canonical)));
        return Map.copyOf(result);
    }

    @Override
    public void writeBatch(Collection<ManualProgressRecord> records) {
        Objects.requireNonNull(records, "manual progress records");
        delegate.writeBatch(records.stream().map(record -> withProvider(record,
                LegacyProviderIdentifiers.historicalStorageKey(record.providerId()))).toList());
    }

    private static ManualProgressRecord withProvider(ManualProgressRecord record, ProviderId providerId) {
        Objects.requireNonNull(record, "manual progress record");
        return new ManualProgressRecord(providerId, record.metricId(), record.playerId(), record.value(),
                record.updateVersion(), record.provenance(), record.updatedAt());
    }
}
