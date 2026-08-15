package net.maddkraft.maddprestige.testkit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.provider.CapabilityDescriptor;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.core.rank.ExternalGroupCatalog;

public final class FakeRankProvider extends FakeProvider implements ExternalGroupCatalog {
    private final Set<String> existingGroups;

    public FakeRankProvider(Set<String> existingGroups) {
        super(new ProviderId("fake_rank"), "rank",
                List.of(new CapabilityDescriptor("direct-membership", "rank", "Fake direct membership projection", java.util.Map.of())));
        this.existingGroups = Set.copyOf(new HashSet<>(existingGroups));
    }

    @Override
    public Result<Boolean> exists(String groupName) {
        return Result.success(existingGroups.contains(groupName));
    }

    public Set<String> existingGroups() {
        return existingGroups;
    }
}
