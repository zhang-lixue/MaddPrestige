package net.maddkraft.maddprestige.testkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.maddkraft.maddprestige.core.yaml.LosslessYamlDocument;

public final class GoldenConfigHelper {
    private GoldenConfigHelper() {
    }

    public static LosslessYamlDocument load(Path path) throws IOException {
        return LosslessYamlDocument.parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static boolean exactlyMatches(Path expected, LosslessYamlDocument actual) throws IOException {
        return Files.readString(expected, StandardCharsets.UTF_8).equals(actual.render());
    }
}
