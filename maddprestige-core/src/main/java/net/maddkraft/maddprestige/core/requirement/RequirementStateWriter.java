package net.maddkraft.maddprestige.core.requirement;

public interface RequirementStateWriter extends RequirementStateReader {
    RequirementBaseline initializeBaseline(RequirementBaseline baseline);

    RequirementLatch recordLatch(RequirementLatch latch);
}
