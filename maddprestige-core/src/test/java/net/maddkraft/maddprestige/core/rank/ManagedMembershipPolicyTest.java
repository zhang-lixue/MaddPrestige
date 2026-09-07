package net.maddkraft.maddprestige.core.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.maddkraft.maddprestige.api.result.Result;
import net.maddkraft.maddprestige.api.result.StructuredError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagedMembershipPolicyTest {
    @Test
    @DisplayName("[A06]Only explicitly configured direct progression groups are mutable")
    void isolatesManagedMemberships() {
        ManagedMembershipDelta delta = new ManagedMembershipPolicy().plan(
                Set.of("member", "supporter", "staff", "temporary-event"),
                Set.of("member", "veteran"), Optional.of("veteran"));
        assertEquals(Set.of("member"), delta.remove());
        assertEquals(Set.of("veteran"), delta.add());
        assertTrue(delta.preserve().containsAll(Set.of("supporter", "staff", "temporary-event")));
    }

    @Test
    @DisplayName("[A05]Missing group is a structured error and catalog has no creation operation")
    void representsMissingGroupWithoutCreationApi() {
        ExternalGroupCatalog missing = group -> Result.success(false);
        var report = new RankProjectionValidator().validate("stages.veteran", ProjectionPolicy.GROUP,
                Optional.of("veteran"), missing);
        assertTrue(report.hasErrors());
        assertEquals("rank.group.not_found", report.findings().getFirst().code());
        assertEquals(Set.of("exists"), java.util.Arrays.stream(ExternalGroupCatalog.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));

        ExternalGroupCatalog unavailable = group -> Result.failure(
                StructuredError.unavailable("rank.catalog.offline", "Rank provider is unavailable"));
        assertEquals("rank.group.unavailable", new RankProjectionValidator()
                .validate("stages.veteran", ProjectionPolicy.GROUP, Optional.of("veteran"), unavailable)
                .findings().getFirst().code());
    }
}
