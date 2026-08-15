package net.maddkraft.maddprestige.core.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

    private static String resource(String name) throws IOException, URISyntaxException {
        var resource = LosslessYamlDocumentTest.class.getResource("/golden/" + name);
        if (resource == null) {
            throw new IllegalStateException("Missing golden resource " + name);
        }
        return Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
    }
}
