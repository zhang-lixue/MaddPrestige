package net.maddkraft.maddprestige.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhaseEightFReleasePackageIT {
    private static final String VERSION = "2.0.0-rc.1";
    private static final Set<String> REQUIRED = Set.of(
            "plugin.yml",
            "config.yml",
            "defaults/progression.yml",
            "defaults/requirements.yml",
            "defaults/rewards.yml",
            "defaults/lifecycle.yml",
            "integrations.yml",
            "locales/en_US.yml",
            "THIRD-PARTY-NOTICES.txt",
            "META-INF/MANIFEST.MF",
            "META-INF/services/java.sql.Driver",
            "sqlite-jdbc.properties",
            "META-INF/maven/org.xerial/sqlite-jdbc/LICENSE",
            "gg/maddkraft/prestige/MaddPrestigePlugin.class",
            "net/maddkraft/maddprestige/api/service/MaddPrestigeService.class",
            "net/maddkraft/maddprestige/platform/paper/bootstrap/MaddPrestigeV2Plugin.class",
            "org/sqlite/JDBC.class",
            "org/snakeyaml/engine/v2/api/Load.class");
    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "qualification/",
            "examples/",
            "net/maddkraft/qualification/",
            "org/junit/",
            "org/mockito/",
            "net/luckperms/",
            "net/milkbowl/vault/",
            "me/clip/placeholderapi/",
            "com/gmail/nossr50/",
            "com/sk89q/",
            "net/momirealms/",
            "com/ghostchu/",
            "me/gypopo/",
            "com/mysql/",
            "org/mariadb/",
            "com/zaxxer/");
    private static final Pattern FORBIDDEN_FILE = Pattern.compile(
            "(?i)(?:^|/)(?:target|world|world_nether|world_the_end|cache|caches)(?:/|$)"
                    + "|(?:^|/)(?:\\.env|credentials?|secrets?|eula\\.txt)$"
                    + "|\\.(?:db|sqlite|sqlite3|log|jfr|zip|jar|java)$");

    @Test
    @DisplayName("[8F] Final distribution has exact release identity and bounded runtime contents")
    void finalDistributionContainsOnlyIntendedRuntimeMaterial() throws IOException {
        String candidateProperty = System.getProperty("phase8f.distribution");
        assertNotNull(candidateProperty, "Failsafe must supply the final distribution path");
        Path candidate = Path.of(candidateProperty).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(candidate), candidate.toString());

        try (ZipFile archive = new ZipFile(candidate.toFile())) {
            HashSet<String> entries = new HashSet<>();
            var enumeration = archive.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                assertTrue(entries.add(entry.getName()), "duplicate ZIP entry " + entry.getName());
            }

            assertTrue(entries.containsAll(REQUIRED), () -> "missing runtime entries " + difference(REQUIRED, entries));
            assertEquals(1, entries.stream().filter("plugin.yml"::equals).count());
            assertEquals(1, entries.stream().filter("META-INF/services/java.sql.Driver"::equals).count());
            entries.forEach(name -> {
                assertTrue(FORBIDDEN_PREFIXES.stream().noneMatch(name::startsWith),
                        () -> "forbidden bundled dependency or harness entry " + name);
                assertFalse(FORBIDDEN_FILE.matcher(name).find(), () -> "forbidden release entry " + name);
            });

            String plugin = text(archive, "plugin.yml");
            assertTrue(plugin.contains("version: '" + VERSION + "'"), plugin);
            assertTrue(plugin.contains(
                    "main: net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin"));
            assertFalse(plugin.contains("SNAPSHOT"));

            for (String schema : List.of("defaults/progression.yml", "defaults/requirements.yml",
                    "defaults/rewards.yml", "defaults/lifecycle.yml", "integrations.yml")) {
                assertTrue(text(archive, schema).contains("schema-version:"), schema);
            }

            String legacyConfig = text(archive, "config.yml");
            assertTrue(legacyConfig.contains("progression-groups:"));
            assertTrue(legacyConfig.contains("quickshop-earned-weight: 0.25"));
            assertTrue(legacyConfig.contains("maddkraft.patron.knave: 3"));
            assertFalse(legacyConfig.contains("schema-version:"),
                    "legacy root config must remain distinct from V2 schema documents");
            assertTrue(entries.stream().noneMatch(name -> name.contains("member-adventurer-veteran")),
                    "the generic example is repository-only and must not be packaged");

            try (InputStream stream = archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF"))) {
                Attributes attributes = new Manifest(stream).getMainAttributes();
                assertEquals(VERSION, attributes.getValue("Implementation-Version"));
                assertEquals("release-candidate", attributes.getValue("MaddPrestige-Release-Channel"));
                assertEquals("2.x-stable-1", attributes.getValue("MaddPrestige-Compatibility-Baseline"));
            }

            String notices = text(archive, "THIRD-PARTY-NOTICES.txt");
            assertTrue(notices.contains("Xerial SQLite JDBC 3.50.3.0"));
            assertTrue(notices.contains("SnakeYAML Engine 3.0.1"));
            assertTrue(notices.contains("Apache License 2.0"));
            assertEquals("org.sqlite.JDBC", text(archive, "META-INF/services/java.sql.Driver").strip());

            for (String name : entries) {
                if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")
                        || name.endsWith(".xml") || name.endsWith(".txt") || name.endsWith(".MF")) {
                    String content = text(archive, name);
                    assertFalse(content.contains("C:\\Users\\"), () -> "local Windows path in " + name);
                    assertFalse(content.contains("/home/runner/"), () -> "CI-local path in " + name);
                } else if (name.startsWith("net/maddkraft/") && name.endsWith(".class")) {
                    String constants = new String(bytes(archive, name), StandardCharsets.ISO_8859_1);
                    assertFalse(constants.contains("Phase 8D candidate"), () -> "stale phase banner in " + name);
                    assertFalse(constants.contains("2.0.0-SNAPSHOT"), () -> "stale snapshot identity in " + name);
                }
            }
        }
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        HashSet<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return Set.copyOf(missing);
    }

    private static String text(ZipFile archive, String name) throws IOException {
        return new String(bytes(archive, name), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(ZipFile archive, String name) throws IOException {
        ZipEntry entry = archive.getEntry(name);
        assertNotNull(entry, name);
        try (InputStream stream = archive.getInputStream(entry)) {
            return stream.readAllBytes();
        }
    }
}
