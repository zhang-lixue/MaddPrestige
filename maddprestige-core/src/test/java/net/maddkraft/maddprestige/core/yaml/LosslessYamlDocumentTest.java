package net.maddkraft.maddprestige.core.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LosslessYamlDocumentTest {
    @Test
    @DisplayName("[A38][Phase1-hard-3] Golden edit preserves comments/order/dotted keys/unknown keys/UTF-8/multiple docs")
    void performsLosslessGoldenRoundTrip() throws IOException, URISyntaxException {
        String input = resource("roundtrip-input.yml");
        String expected = resource("roundtrip-expected.yml");
        LosslessYamlDocument original = LosslessYamlDocument.parse(input);
        assertEquals(input, original.render());
        assertEquals(2, original.documentCount());

        LosslessYamlDocument edited = original
                .replaceBoolean(YamlPath.document(0).key("permissions").key("maddkraft.homes.1"), false)
                .replaceString(YamlPath.document(0).key("stages").index(0).key("display-name"), "Veteran \"Elite\"")
                .replaceDecimal(YamlPath.document(1).key("weight"), "0");

        assertEquals(expected, edited.render());
        assertEquals("keep me", edited.scalar(YamlPath.document(0).key("unknown-plugin").key("untouched")));
        assertEquals("Hello, 世界", edited.scalar(YamlPath.document(1).key("message")));
    }

    @Test
    @DisplayName("[A38-correction] Every scalar replacement rejects literal and folded block styles")
    void rejectsEveryBlockScalarMutation() {
        LosslessYamlDocument document = LosslessYamlDocument.parse("""
                literal: |
                  true
                folded: >
                  12.5
                """);
        YamlPath literal = YamlPath.document(0).key("literal");
        YamlPath folded = YamlPath.document(0).key("folded");

        assertThrows(IllegalArgumentException.class, () -> document.replaceString(literal, "changed"));
        assertThrows(IllegalArgumentException.class, () -> document.replaceString(folded, "changed"));
        assertThrows(IllegalArgumentException.class, () -> document.replaceBoolean(literal, false));
        assertThrows(IllegalArgumentException.class, () -> document.replaceBoolean(folded, false));
        assertThrows(IllegalArgumentException.class, () -> document.replaceDecimal(literal, "1"));
        assertThrows(IllegalArgumentException.class, () -> document.replaceDecimal(folded, "1"));
    }

    @Test
    @DisplayName("[A38-correction] CRLF bytes remain intact around a surgical scalar edit")
    void retainsCrLfSource() {
        String source = "# windows source\r\nfirst: keep\r\ntarget: old\r\nlast: keep\r\n";
        String expected = "# windows source\r\nfirst: keep\r\ntarget: new\r\nlast: keep\r\n";

        LosslessYamlDocument edited = LosslessYamlDocument.parse(source)
                .replaceString(YamlPath.document(0).key("target"), "new");

        assertEquals(expected, edited.render());
    }

    @Test
    @DisplayName("[A38-correction] Source marks before supplementary Unicode map to UTF-16 offsets safely")
    void editsAfterSupplementaryPlaneUnicode() {
        String source = "message: \"Ready 😀\"\ntarget: old\n";
        String expected = "message: \"Ready 😀\"\ntarget: changed\n";

        LosslessYamlDocument edited = LosslessYamlDocument.parse(source)
                .replaceString(YamlPath.document(0).key("target"), "changed");

        assertEquals(expected, edited.render());
        assertEquals("Ready 😀", edited.scalar(YamlPath.document(0).key("message")));
    }

    @Test
    @DisplayName("[A38-correction] Ambiguous and escaped replacement strings remain YAML strings")
    void safelyQuotesAmbiguousReplacementStrings() {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("alpha", "alpha");
        cases.put("true", "\"true\"");
        cases.put("FALSE", "\"FALSE\"");
        cases.put("null", "\"null\"");
        cases.put("123", "\"123\"");
        cases.put("-4.50", "\"-4.50\"");
        cases.put("key: value", "\"key: value\"");
        cases.put("hash # value", "\"hash # value\"");
        cases.put(" leading and trailing ", "\" leading and trailing \"");
        cases.put("say \"hello\"", "\"say \\\"hello\\\"\"");
        cases.put("C:\\temp\\file", "\"C:\\\\temp\\\\file\"");
        cases.put("line\nbreak\tvalue", "\"line\\nbreak\\tvalue\"");
        cases.put("\0\b\f", "\"\\0\\b\\f\"");

        for (Map.Entry<String, String> testCase : cases.entrySet()) {
            LosslessYamlDocument edited = LosslessYamlDocument.parse("value: original\n")
                    .replaceString(YamlPath.document(0).key("value"), testCase.getKey());
            assertEquals("value: " + testCase.getValue() + "\n", edited.render());
            assertEquals(testCase.getKey(), edited.scalar(YamlPath.document(0).key("value")));
        }
    }

    @Test
    @DisplayName("[A38-correction] Anchors and explicit custom/standard tags fail closed")
    void protectsAnchorAndTagSemantics() {
        String source = """
                anchored: &shared original
                alias: *shared
                custom: !phase1 original
                standard: !!str original
                """;
        LosslessYamlDocument document = LosslessYamlDocument.parse(source);

        assertThrows(IllegalArgumentException.class,
                () -> document.replaceString(YamlPath.document(0).key("anchored"), "changed"));
        assertThrows(IllegalArgumentException.class,
                () -> document.replaceString(YamlPath.document(0).key("alias"), "changed"));
        assertThrows(IllegalArgumentException.class,
                () -> document.replaceString(YamlPath.document(0).key("custom"), "changed"));
        assertThrows(IllegalArgumentException.class,
                () -> document.replaceString(YamlPath.document(0).key("standard"), "changed"));
    }

    @Test
    @DisplayName("[A38][A42] Structural list/map edits preserve CRLF, comments, order, and empty collection type")
    void structurallyEditsWithoutReformattingDocument() {
        String source = "# owner\r\nstages: {} # stage note\r\norder: [] # order note\r\ntail: keep\r\n";
        YamlPath stages = YamlPath.document(0).key("stages");
        YamlPath order = YamlPath.document(0).key("order");

        LosslessYamlDocument added = LosslessYamlDocument.parse(source)
                .appendMappingBlock(stages, "member", java.util.List.of("enabled: true", "projection: none"))
                .appendSequenceString(order, "member");

        assertEquals("# owner\r\nstages:  # stage note\r\n  member:\r\n    enabled: true\r\n"
                + "    projection: none\r\norder:  # order note\r\n  - member\r\ntail: keep\r\n", added.render());
        assertEquals(java.util.List.of("member"), added.mappingKeys(stages));
        assertEquals(java.util.List.of("member"), added.sequenceScalars(order));

        LosslessYamlDocument removed = added.removeMappingEntry(stages, "member")
                .removeSequenceString(order, "member");
        assertEquals("# owner\r\nstages: {} # stage note\r\norder: [] # order note\r\ntail: keep\r\n",
                removed.render());
        assertEquals(java.util.List.of(), removed.mappingKeys(stages));
        assertEquals(java.util.List.of(), removed.sequenceScalars(order));
    }

    @Test
    @DisplayName("[A38][A42] Repeated block-sequence additions retain the owning list indentation")
    void appendsMultipleSequenceValuesAtTheListIndent() {
        YamlPath order = YamlPath.document(0).key("order");

        LosslessYamlDocument edited = LosslessYamlDocument.parse("order: []\ntail: keep\n")
                .appendSequenceString(order, "first")
                .appendSequenceString(order, "second");

        assertEquals("order: \n  - first\n  - second\ntail: keep\n", edited.render());
        assertEquals(java.util.List.of("first", "second"), edited.sequenceScalars(order));
    }

    @Test
    @DisplayName("[Phase 9C] Omitted safe defaults materialize as canonical nested scalars and lists")
    void materializesOmittedConfigurationPaths() {
        LosslessYamlDocument edited = LosslessYamlDocument.parse("schema-version: 4\n")
                .setBoolean(YamlPath.document(0).key("prestige").key("enabled"), true)
                .setString(YamlPath.document(0).key("prestige").key("maximum"), "unlimited")
                .appendSequenceString(YamlPath.document(0).key("prestige").key("costs"), "payment")
                .appendSequenceString(YamlPath.document(0).key("prestige").key("costs"), "second");

        assertEquals("""
                schema-version: 4
                prestige:
                  enabled: true
                  maximum: unlimited
                  costs:
                    - payment
                    - second
                """, edited.render());
    }

    @Test
    @DisplayName("[Phase 9F-C2] Missing mapping scalars materialize below an existing sequence item")
    void materializesMappingScalarBelowExistingSequenceItem() {
        String source = """
                scaling:
                  segments:
                    - start-prestige: 1
                      overrides: # preserve owner note
                        3: 3
                sibling: keep
                """;

        LosslessYamlDocument edited = LosslessYamlDocument.parse(source).setDecimal(
                YamlPath.document(0).key("scaling").key("segments").index(0).key("overrides").key("6"),
                "4");

        assertEquals("4", edited.scalar(
                YamlPath.document(0).key("scaling").key("segments").index(0).key("overrides").key("6")));
        assertTrue(edited.render().contains("overrides: # preserve owner note"));
        assertTrue(edited.render().contains("\"6\": 4"));
        assertTrue(edited.render().endsWith("sibling: keep\n"));
    }

    @Test
    @DisplayName("[Phase 9C correction] Structured map and sequence edits preserve surrounding YAML")
    void createsEditsAndRemovesStructuredValuesLosslessly() {
        String source = "# owner\r\nschema-version: 4\r\nprestige:\r\n  enabled: true\r\ntail: keep\r\n";
        YamlPath segments = YamlPath.document(0).key("prestige").key("cost-scaling")
                .key("payment").key("segments");
        LosslessYamlDocument added = LosslessYamlDocument.parse(source).appendSequenceStructure(segments, Map.of(
                "start-prestige", 1L, "end-prestige", "unlimited", "mode", "LINEAR",
                "base", new java.math.BigDecimal("10"), "rate", new java.math.BigDecimal("2")));

        assertEquals("1", added.scalar(segments.index(0).key("start-prestige")));
        assertEquals("2", added.scalar(segments.index(0).key("rate")));
        assertTrue(added.render().startsWith("# owner\r\n"));
        assertTrue(added.render().endsWith("tail: keep\r\n"));

        LosslessYamlDocument appended = added.appendSequenceStructure(segments, Map.of(
                "start-prestige", 4L, "end-prestige", "unlimited", "mode", "FLAT",
                "transition", "CONTINUE", "base", java.math.BigDecimal.ONE));
        assertEquals("1", appended.scalar(segments.index(0).key("start-prestige")));
        assertEquals("4", appended.scalar(segments.index(1).key("start-prestige")));
        assertEquals("CONTINUE", appended.scalar(segments.index(1).key("transition")));

        LosslessYamlDocument edited = appended.replaceSequenceStructure(segments, 0, Map.of(
                "start-prestige", 1L, "end-prestige", "unlimited", "mode", "FLAT",
                "base", new java.math.BigDecimal("25")));
        assertEquals("25", edited.scalar(segments.index(0).key("base")));
        assertThrows(IllegalArgumentException.class, () -> edited.scalar(segments.index(0).key("rate")));

        LosslessYamlDocument removedFirst = edited.removeSequenceIndex(segments, 0);
        assertEquals("4", removedFirst.scalar(segments.index(0).key("start-prestige")));
        assertEquals("CONTINUE", removedFirst.scalar(segments.index(0).key("transition")));

        LosslessYamlDocument removedLast = removedFirst.removeSequenceIndex(segments, 0);
        assertEquals(List.of(), removedLast.sequenceScalars(segments));
        assertTrue(removedLast.render().startsWith("# owner\r\n"));
        assertTrue(removedLast.render().endsWith("tail: keep\r\n"));
    }

    @Test
    @DisplayName("[Phase 9D] Two scaling segments append before an existing sibling requirement")
    void appendsTwoScalingSegmentsBeforeExistingSiblingRequirement() {
        String source = """
                schema-version: 3
                requirements:
                  phase9d_vault_balance:
                    completion: LIVE
                    metric: balance
                    provider: vault_balance
                    scope: ABSOLUTE
                    target: "2"
                    value-type: CURRENCY_AMOUNT
                    scaling:
                      mode: MANUAL
                      base: 1
                      rate: 0
                  phase9d_mcmmo_total_level:
                    completion: LIVE
                    metric: total_level
                    provider: mcmmo
                    scope: ABSOLUTE
                    target: "1"
                    value-type: INTEGER_COUNT
                """;
        YamlPath segments = YamlPath.document(0).key("requirements").key("phase9d_vault_balance")
                .key("scaling").key("segments");

        LosslessYamlDocument first = LosslessYamlDocument.parse(source).appendSequenceStructure(segments,
                new java.util.LinkedHashMap<>(Map.of(
                        "start-prestige", 1L,
                        "end-prestige", "3",
                        "mode", "MANUAL",
                        "transition", "EXPLICIT_BASE",
                        "base", java.math.BigDecimal.ONE,
                        "rate", java.math.BigDecimal.ZERO,
                        "overrides", Map.of("1", java.math.BigDecimal.ONE,
                                "2", new java.math.BigDecimal("2"),
                                "3", new java.math.BigDecimal("3")))));
        LosslessYamlDocument second = first.appendSequenceStructure(segments, new java.util.LinkedHashMap<>(Map.of(
                "start-prestige", 4L,
                "end-prestige", "unlimited",
                "mode", "FLAT",
                "transition", "CONTINUE",
                "base", java.math.BigDecimal.ONE,
                "rate", java.math.BigDecimal.ZERO)));

        assertEquals("1", second.scalar(segments.index(0).key("start-prestige")));
        assertEquals("3", second.scalar(segments.index(0).key("overrides").key("3")));
        assertEquals("4", second.scalar(segments.index(1).key("start-prestige")));
        assertEquals("CONTINUE", second.scalar(segments.index(1).key("transition")));
        assertEquals("mcmmo", second.scalar(YamlPath.document(0).key("requirements")
                .key("phase9d_mcmmo_total_level").key("provider")));

        LosslessYamlDocument firstReplaced = second.replaceSequenceStructure(segments, 0, Map.of(
                "start-prestige", 1L,
                "end-prestige", "2",
                "mode", "FLAT",
                "transition", "EXPLICIT_BASE",
                "base", new java.math.BigDecimal("2"),
                "rate", java.math.BigDecimal.ZERO));
        LosslessYamlDocument bothReplaced = firstReplaced.replaceSequenceStructure(segments, 1, Map.of(
                "start-prestige", 3L,
                "end-prestige", "unlimited",
                "mode", "LINEAR",
                "transition", "CONTINUE",
                "base", java.math.BigDecimal.ONE,
                "rate", java.math.BigDecimal.ONE));

        assertEquals("2", bothReplaced.scalar(segments.index(0).key("end-prestige")));
        assertEquals("3", bothReplaced.scalar(segments.index(1).key("start-prestige")));
        assertEquals("CONTINUE", bothReplaced.scalar(segments.index(1).key("transition")));
        assertEquals("mcmmo", bothReplaced.scalar(YamlPath.document(0).key("requirements")
                .key("phase9d_mcmmo_total_level").key("provider")));
    }

    @Test
    @DisplayName("[A42][A69] Removing a middle block mapping and sequence value retains neighboring stages")
    void removesMiddleStructuralValuesExactly() {
        String source = """
                stages:
                  a:
                    enabled: true
                    projection: none
                  b:
                    enabled: true
                    projection: none
                  c:
                    enabled: true
                    projection: none
                order:
                  - a
                  - b
                  - c
                """;
        String expected = """
                stages:
                  a:
                    enabled: true
                    projection: none
                  c:
                    enabled: true
                    projection: none
                order:
                  - a
                  - c
                """;

        LosslessYamlDocument removed = LosslessYamlDocument.parse(source)
                .removeMappingEntry(YamlPath.document(0).key("stages"), "b")
                .removeSequenceString(YamlPath.document(0).key("order"), "b");

        assertEquals(expected, removed.render());
        assertEquals(java.util.List.of("a", "c"), removed.mappingKeys(YamlPath.document(0).key("stages")));
        assertEquals(java.util.List.of("a", "c"), removed.sequenceScalars(YamlPath.document(0).key("order")));
    }

    private static String resource(String name) throws IOException, URISyntaxException {
        var resource = LosslessYamlDocumentTest.class.getResource("/golden/" + name);
        if (resource == null) {
            throw new IllegalStateException("Missing golden resource " + name);
        }
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
