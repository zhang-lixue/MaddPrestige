package net.maddkraft.maddprestige.api.rank;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.maddkraft.maddprestige.api.provider.Provider;
import net.maddkraft.maddprestige.api.result.Result;

public interface RankAdapter extends Provider {
    CompletionStage<Result<Set<String>>> validateTargets(Set<String> groupNames);

    CompletionStage<Result<ManagedRankState>> readManagedState(UUID playerId, Set<String> managedGroups);

    CompletionStage<Result<RankProjectionResult>> project(RankProjectionRequest request);
}
