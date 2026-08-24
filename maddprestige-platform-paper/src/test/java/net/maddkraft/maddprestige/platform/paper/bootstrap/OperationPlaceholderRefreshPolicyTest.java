package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.service.OperationKind;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import org.junit.jupiter.api.Test;

class OperationPlaceholderRefreshPolicyTest {
    @Test
    void refreshesOnlyOperationsThatCrossedThePreDurabilityBoundary() {
        UUID request = UUID.randomUUID();
        OperationResult cancelled = new OperationResult(request, Optional.empty(), OperationKind.RANK_UP,
                OperationStatus.BLOCKED, Optional.empty());
        OperationResult durable = new OperationResult(request, Optional.of(new OperationId(UUID.randomUUID())),
                OperationKind.RANK_UP, OperationStatus.COMPLETED, Optional.empty());

        assertFalse(OperationPlaceholderRefreshPolicy.shouldRefresh(cancelled, null));
        assertFalse(OperationPlaceholderRefreshPolicy.shouldRefresh(durable,
                new IllegalStateException("controlled failure")));
        assertTrue(OperationPlaceholderRefreshPolicy.shouldRefresh(durable, null));
    }
}
