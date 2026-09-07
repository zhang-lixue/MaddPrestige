package net.maddkraft.maddprestige.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import net.maddkraft.maddprestige.api.annotation.Experimental;
import net.maddkraft.maddprestige.api.annotation.Stable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StableApiCompatibilityBaselineTest {
    private static final String API_PACKAGE = "net.maddkraft.maddprestige.api";
    private static final String BASELINE_SHA256 = "357646DE87CE5B06883B8678CCB3A45D655E5F08A7219B63BDEDDA4B68402C4E";

    @Test
    @DisplayName("The 46-type Bukkit-free Stable 2.x surface matches its frozen binary signature")
    void stableApiMatchesFrozenSignature() throws Exception {
        List<? extends Class<?>> stableTypes = stableTypes();
        assertEquals(46, stableTypes.size(), "Stable additions or removals require compatibility-baseline review");

        ArrayList<String> signatures = new ArrayList<>();
        stableTypes.forEach(type -> collect(type, signatures));
        signatures.sort(String::compareTo);
        String hash = sha256(String.join("\n", signatures) + "\n");
        assertEquals(BASELINE_SHA256, hash,
                () -> "Stable 2.x signature changed; actual SHA-256 " + hash
                        + ". Regenerate and independently review the complete API inventory before changing the baseline.");
    }

    private static List<? extends Class<?>> stableTypes() throws IOException, URISyntaxException {
        Path classes = Path.of(Stable.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packageRoot = classes.resolve(API_PACKAGE.replace('.', '/'));
        try (var files = Files.walk(packageRoot)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !path.getFileName().toString().contains("$"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.class"))
                    .map(path -> load(classes, path))
                    .filter(type -> Modifier.isPublic(type.getModifiers()))
                    .filter(StableApiCompatibilityBaselineTest::isStable)
                    .sorted(Comparator.comparing((Class<?> type) -> type.getName()))
                    .toList();
        }
    }

    private static Class<?> load(Path classes, Path file) {
        String name = classes.relativize(file).toString()
                .replace(file.getFileSystem().getSeparator(), ".")
                .replaceFirst("\\.class$", "");
        try {
            return Class.forName(name, false, StableApiCompatibilityBaselineTest.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean isStable(Class<?> type) {
        return type == Stable.class || type == Experimental.class
                || type.isAnnotationPresent(Stable.class)
                || type.getPackage().isAnnotationPresent(Stable.class);
    }

    private static void collect(Class<?> type, List<String> lines) {
        lines.add("TYPE " + type.toGenericString());
        if (type.getGenericSuperclass() != null) {
            lines.add("SUPER " + type.getName() + ' ' + type.getGenericSuperclass().getTypeName());
        }
        for (var contract : type.getGenericInterfaces()) {
            lines.add("INTERFACE " + type.getName() + ' ' + contract.getTypeName());
        }
        annotationLines("TYPE-ANNOTATION " + type.getName() + ' ', type.getDeclaredAnnotations(), lines);
        for (var constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                lines.add("CONSTRUCTOR " + constructor.toGenericString());
                annotationLines("CONSTRUCTOR-ANNOTATION " + constructor.toGenericString() + ' ',
                        constructor.getDeclaredAnnotations(), lines);
            }
        }
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                lines.add("METHOD " + method.toGenericString());
                annotationLines("METHOD-ANNOTATION " + method.toGenericString() + ' ',
                        method.getDeclaredAnnotations(), lines);
                if (method.getDefaultValue() != null) {
                    lines.add("METHOD-DEFAULT " + method.toGenericString() + ' ' + method.getDefaultValue());
                }
            }
        }
        for (var field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                lines.add("FIELD " + field.toGenericString());
                annotationLines("FIELD-ANNOTATION " + field.toGenericString() + ' ',
                        field.getDeclaredAnnotations(), lines);
            }
        }
        for (var nested : type.getDeclaredClasses()) {
            if (Modifier.isPublic(nested.getModifiers())) {
                lines.add("NESTED " + nested.toGenericString());
            }
        }
        if (type.isRecord()) {
            for (var component : type.getRecordComponents()) {
                lines.add("RECORD-COMPONENT " + type.getName() + ' ' + component.getGenericType().getTypeName()
                        + ' ' + component.getName());
                annotationLines("RECORD-COMPONENT-ANNOTATION " + type.getName() + ' ' + component.getName() + ' ',
                        component.getDeclaredAnnotations(), lines);
            }
        }
    }

    private static void annotationLines(String prefix, java.lang.annotation.Annotation[] annotations,
            List<String> lines) {
        java.util.Arrays.stream(annotations).map(Object::toString).sorted().forEach(value -> lines.add(prefix + value));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
