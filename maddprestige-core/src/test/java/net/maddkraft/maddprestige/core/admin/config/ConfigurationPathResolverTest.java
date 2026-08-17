package net.maddkraft.maddprestige.core.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.maddkraft.maddprestige.core.yaml.YamlPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurationPathResolverTest {
    private final ConfigurationPathResolver resolver = new ConfigurationPathResolver();

    @Test
    @DisplayName("[R1] Iterative parser preserves every canonical root and segment form")
    void resolvesAcceptedCanonicalGrammarExactly() {
        assertResolves("progression", "progression.yml");
        assertResolves("progression.stages.default.display-name", "progression.yml",
                "stages", "default", "display-name");
        assertResolves("requirements.rule_1.metric-2", "requirements.yml", "rule_1", "metric-2");
        assertResolves("rewards.grant", "rewards.yml", "grant");
        assertResolves("integrations.placeholderapi", "integrations.yml", "placeholderapi");
        assertResolves("prestige", "lifecycle.yml", "prestige");
        assertResolves("prestige.enabled", "lifecycle.yml", "prestige", "enabled");
        assertResolves("currencies.primary", "lifecycle.yml", "currencies", "primary");
        assertResolves("entitlements.vip", "lifecycle.yml", "entitlements", "vip");
        assertResolves("milestones.first", "lifecycle.yml", "milestones", "first");
        assertResolves("seasons.current", "lifecycle.yml", "seasons", "current");
        assertResolves("competition.leaderboard", "lifecycle.yml", "competition", "leaderboard");
    }

    @Test
    @DisplayName("[R1] Iterative parser preserves malformed and unknown path rejection")
    void rejectsEveryFormOutsideTheAcceptedGrammar() {
        for (String path : List.of(
                "",
                ".progression",
                "progression.",
                "progression..stages",
                "Progression.stages",
                "progression stages",
                "progression/stages",
                "progression.stages[0]",
                "progression.*",
                "progression.stages\\value",
                "progressión.stages",
                "unknown.valid-path")) {
            assertFalse(resolver.resolve(path).isPresent(), path);
        }
        NullPointerException failure = assertThrows(NullPointerException.class, () -> resolver.resolve(null));
        assertEquals("canonical path", failure.getMessage());
    }

    @Test
    @DisplayName("[R1] Large valid inputs complete with linear segment construction and bounded stack")
    void resolvesLargeValidInputsWithoutRecursiveParsing() {
        String longToken = "a".repeat(100_000);
        ConfigurationPathResolver.ResolvedPath single = resolver.resolve("progression." + longToken).orElseThrow();
        assertEquals(1, single.yamlPath().segments().size());
        assertEquals(new YamlPath.Key(longToken), single.yamlPath().segments().getFirst());

        String manyComponents = "progression" + ".component".repeat(20_000);
        ConfigurationPathResolver.ResolvedPath repeated = resolver.resolve(manyComponents).orElseThrow();
        assertEquals(20_000, repeated.yamlPath().segments().size());
        assertEquals(new YamlPath.Key("component"), repeated.yamlPath().segments().getFirst());
        assertEquals(new YamlPath.Key("component"), repeated.yamlPath().segments().getLast());
    }

    @Test
    @DisplayName("[R1] Large adversarial delimiter and suffix inputs remain rejected without stack growth")
    void rejectsLargeMalformedInputsWithoutRecursiveParsing() {
        String manyComponents = "progression" + ".component".repeat(20_000);
        assertFalse(resolver.resolve(manyComponents + ".").isPresent());
        assertFalse(resolver.resolve(manyComponents + "#invalid").isPresent());
        assertFalse(resolver.resolve("progression" + ".".repeat(100_000)).isPresent());
    }

    private void assertResolves(String path, String documentName, String... keys) {
        ConfigurationPathResolver.ResolvedPath resolved = resolver.resolve(path).orElseThrow();
        assertEquals(documentName, resolved.documentName());
        assertEquals(List.of(keys).stream().map(YamlPath.Key::new).toList(), resolved.yamlPath().segments());
    }
}
