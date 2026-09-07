package net.maddkraft.maddprestige.core.compatibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegacySourceNormalizationTest {
    private static final Pattern ACTIVE_DIAGNOSTIC = Pattern.compile(
            "\\\"phase(?:3|4|5|9b|9c)\\.[^\\\"]+\\\"");
    private static final List<String> PHASE_METADATA = List.of(
            "\"phase3-foundation\"", "\"phase4\"", "\"phase5\"", "\"phase7\"");

    @Test
    @DisplayName("Active runtime sources contain no legacy diagnostic emission or phase-based provider metadata")
    void activeSourceIdentifiersArePurposeBased() throws IOException {
        Path root = repositoryRoot();
        ArrayList<String> violations = new ArrayList<>();
        try (var modules = Files.list(root)) {
            for (Path module : modules.filter(path -> path.getFileName().toString().startsWith("maddprestige-"))
                    .toList()) {
                Path source = module.resolve("src/main/java");
                if (Files.notExists(source)) {
                    continue;
                }
                try (var paths = Files.walk(source)) {
                    for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                        String normalized = path.toString().replace('\\', '/');
                        if (normalized.endsWith("/LegacyDiagnosticCodes.java")
                                || normalized.endsWith("/LegacyProviderIdentifiers.java")
                                || normalized.endsWith("/LegacyLocaleKeys.java")
                                || normalized.endsWith("/HistoricalMigrationCatalog.java")) {
                            continue;
                        }
                        String sourceText = Files.readString(path, StandardCharsets.UTF_8);
                        if (ACTIVE_DIAGNOSTIC.matcher(sourceText).find()) {
                            violations.add(root.relativize(path) + " contains a legacy diagnostic literal");
                        }
                        for (String metadata : PHASE_METADATA) {
                            if (sourceText.contains(metadata)) {
                                violations.add(root.relativize(path) + " contains phase metadata " + metadata);
                            }
                        }
                    }
                }
            }
        }
        assertFalse(violations.size() > 0, violations.toString());
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && Files.notExists(current.resolve("pom.xml"))) {
            current = current.getParent();
        }
        assertNotNull(current, "repository root");
        return current;
    }
}
