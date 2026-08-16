package net.maddkraft.maddprestige.core.competition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CompetitionFeatureBoundaryTest {
    @Test
    void disabledCompetitionHasNoServiceCommandsOrUserInterface() {
        CompetitionFeatureBoundary boundary = new CompetitionFeatureBoundary(CompetitionConfiguration.disabled());

        assertFalse(boundary.activeService().isPresent());
        assertFalse(boundary.contributesCommands());
        assertFalse(boundary.contributesUserInterface());
        assertThrows(IllegalArgumentException.class,
                () -> new CompetitionFeatureBoundary(new CompetitionConfiguration(true)));
    }
}
