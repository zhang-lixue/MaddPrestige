package net.maddkraft.maddprestige.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.maddkraft.maddprestige.platform.paper.bootstrap.MaddPrestigeV2Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldLifecycleOwnershipTest {
    @Test
    @DisplayName("[A74] runtime integration production sources own no Bukkit world or WorldGuard mutation lifecycle")
    void productionSourcesContainNoWorldLifecycleMutation() throws IOException {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = "maddprestige-platform-paper".equals(module.getFileName().toString())
                ? module.getParent() : module;
        List<Path> roots = List.of(
                repository.resolve("maddprestige-platform-paper/src/main/java"),
                repository.resolve("maddprestige-integrations/src/main/java"));
        List<String> forbidden = List.of("WorldCreator", "Bukkit.createWorld", ".createWorld(",
                ".unloadWorld(", ".deleteWorld(", ".addRegion(", ".removeRegion(",
                ".setFlag(", ".setPriority(", ".setParent(");
        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "Expected source root: " + root);
            try (var paths = Files.walk(root)) {
                for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(source);
                    for (String token : forbidden) {
                        assertFalse(text.contains(token), source + " contains prohibited mutation token " + token);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("[A73] V2 main class has no eager optional-plugin symbolic dependency")
    void compositionRootDoesNotEagerlyLinkOptionalApis() throws IOException {
        String resource = "/" + MaddPrestigeV2Plugin.class.getName().replace('.', '/') + ".class";
        byte[] bytes;
        try (var input = MaddPrestigeV2Plugin.class.getResourceAsStream(resource)) {
            assertTrue(input != null, "Composition-root bytecode is available");
            bytes = input.readAllBytes();
        }
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(constants.contains("net/momirealms"));
        assertFalse(constants.contains("com/sk89q"));
        assertFalse(constants.contains("me/ryanhamshire"));
    }
}
