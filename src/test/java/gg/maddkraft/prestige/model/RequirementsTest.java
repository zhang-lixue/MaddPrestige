package gg.maddkraft.prestige.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementsTest {
    @Test
    void scalesAndDiscountsAllObjectivesPredictably() {
        Requirements base = new Requirements(100.0, 1_000.0, 100, 3, 5, 1);
        Requirements scaled = base.scaled(1.5);
        assertEquals(150.0, scaled.cost());
        assertEquals(1_500.0, scaled.serverEarnings());
        assertEquals(150, scaled.mcMmoXp());
        assertEquals(5, scaled.rabbitHoles());
        assertEquals(8, scaled.decreeObjectives());
        assertEquals(2, scaled.bosses());

        Requirements discounted = scaled.discounted(0.20);
        assertEquals(120.0, discounted.cost());
        assertEquals(1_200.0, discounted.serverEarnings());
        assertEquals(120, discounted.mcMmoXp());
        assertEquals(4, discounted.rabbitHoles());
        assertEquals(7, discounted.decreeObjectives());
        assertEquals(2, discounted.bosses());
    }

    @Test
    void ledgerSatisfactionUsesEveryObjective() {
        Requirements requirements = new Requirements(0, 50, 20, 2, 1, 1);
        RunLedger ledger = new RunLedger(50, 20, 2, 1, 0);
        assertTrue(!ledger.satisfies(requirements));
        ledger.addBosses(1);
        assertTrue(ledger.satisfies(requirements));
    }
}
