package net.maddkraft.maddprestige.platform.paper.bootstrap;

import net.maddkraft.maddprestige.api.service.OperationResult;

/** Keeps presentation refresh behind the PRE durability boundary. */
final class OperationPlaceholderRefreshPolicy {
    private OperationPlaceholderRefreshPolicy() {
    }

    static boolean shouldRefresh(OperationResult result, Throwable failure) {
        return failure == null && result != null && result.durableOperationId().isPresent();
    }
}
