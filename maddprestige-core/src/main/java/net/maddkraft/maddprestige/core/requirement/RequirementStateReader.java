package net.maddkraft.maddprestige.core.requirement;

import java.util.Optional;

public interface RequirementStateReader {
    Optional<RequirementBaseline> findBaseline(BaselineKey key);

    Optional<RequirementLatch> findLatch(LatchKey key);
}
