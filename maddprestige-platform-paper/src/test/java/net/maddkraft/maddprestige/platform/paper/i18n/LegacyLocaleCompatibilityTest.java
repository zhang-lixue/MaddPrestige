package net.maddkraft.maddprestige.platform.paper.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyLocaleCompatibilityTest {
    @TempDir
    Path temporaryDirectory;
    private final ArrayList<String> diagnostics = new ArrayList<>();
    private PaperMessageService messages;

    @BeforeEach
    void open() throws Exception {
        messages = PaperMessageService.open(temporaryDirectory,
                PaperMessageService.class.getClassLoader(), diagnostics::add);
    }

    @Test
    @DisplayName("A selected legacy-only locale override remains readable through the canonical key")
    void legacyOnlySelectedOverrideRemainsCompatible() throws Exception {
        select("""
                phase7.permission_denied: "LEGACY <permission>"
                phase7.usage: "LEGACY USAGE"
                """);

        assertEquals("LEGACY maddprestige.use", plain(messages.render(
                "command.runtime.permission_denied", java.util.Map.of("permission", "maddprestige.use"))));
        assertEquals("LEGACY USAGE", plain(messages.render("command.runtime.usage")));
    }

    @Test
    @DisplayName("Canonical selected locale keys win over exact historical aliases")
    void canonicalSelectedOverrideWinsWhenBothExist() throws Exception {
        select("""
                command.runtime.permission_denied: "CANONICAL <permission>"
                command.runtime.usage: "CANONICAL USAGE"
                phase7.permission_denied: "LEGACY <permission>"
                phase7.usage: "LEGACY USAGE"
                """);

        assertEquals("CANONICAL USAGE", plain(messages.render("command.runtime.usage")));
        assertEquals("CANONICAL USAGE", plain(messages.render("phase7.usage")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.contains("overrides its deprecated alias")));
    }

    @Test
    @DisplayName("The bundled locale exposes only canonical runtime command keys")
    void bundledLocaleContainsOnlyCanonicalKeys() throws Exception {
        assertTrue(messages.requiredKeys().contains("command.runtime.permission_denied"));
        assertTrue(messages.requiredKeys().contains("command.runtime.usage"));
        assertFalse(messages.requiredKeys().contains("phase7.permission_denied"));
        assertFalse(messages.requiredKeys().contains("phase7.usage"));
        String bundled = new String(PaperMessageService.class.getClassLoader()
                .getResourceAsStream("locales/en_US.yml").readAllBytes(), StandardCharsets.UTF_8);
        assertFalse(bundled.contains("phase7."));
    }

    private void select(String catalog) throws Exception {
        Files.writeString(temporaryDirectory.resolve("locale.yml"), "locale: zz_ZZ\n", StandardCharsets.UTF_8);
        Path locales = temporaryDirectory.resolve("locales");
        Files.createDirectories(locales);
        Files.writeString(locales.resolve("zz_ZZ.yml"), catalog, StandardCharsets.UTF_8);
        assertTrue(messages.reload().successful());
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
