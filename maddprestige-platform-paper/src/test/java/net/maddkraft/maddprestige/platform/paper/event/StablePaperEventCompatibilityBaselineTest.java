package net.maddkraft.maddprestige.platform.paper.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StablePaperEventCompatibilityBaselineTest {
    private static final String BASELINE_SHA256 = "48F8824B83FF998C9387FB53EC5363E4BED51FF929F751FE28E3B1C52171EC1C";
    private static final List<? extends Class<?>> EVENTS = List.of(
            ConfigAppliedEvent.class,
            PostPrestigeEvent.class,
            PostRankUpEvent.class,
            PrePrestigeEvent.class,
            PreRankUpEvent.class,
            ProviderHealthChangedEvent.class).stream()
            .sorted(Comparator.comparing(Class::getName))
            .toList();

    @Test
    @DisplayName("The six-type Stable Paper event 2.x surface matches its frozen binary signature")
    void stablePaperEventsMatchFrozenSignature() {
        assertEquals(6, EVENTS.size());
        ArrayList<String> signatures = new ArrayList<>();
        EVENTS.forEach(type -> collect(type, signatures));
        signatures.sort(String::compareTo);
        String hash = sha256(String.join("\n", signatures) + "\n");
        assertEquals(BASELINE_SHA256, hash,
                () -> "Stable Paper event signature changed; actual SHA-256 " + hash
                        + ". Regenerate and independently review the Paper API inventory before changing the baseline.");
    }

    private static void collect(Class<?> type, List<String> lines) {
        lines.add("TYPE " + type.toGenericString());
        lines.add("SUPER " + type.getName() + ' ' + type.getGenericSuperclass().getTypeName());
        for (var contract : type.getGenericInterfaces()) {
            lines.add("INTERFACE " + type.getName() + ' ' + contract.getTypeName());
        }
        java.util.Arrays.stream(type.getDeclaredAnnotations()).map(Object::toString).sorted()
                .forEach(value -> lines.add("TYPE-ANNOTATION " + type.getName() + ' ' + value));
        for (var constructor : type.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers())) {
                lines.add("CONSTRUCTOR " + constructor.toGenericString());
            }
        }
        for (var method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                lines.add("METHOD " + method.toGenericString());
            }
        }
        for (var field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())) {
                lines.add("FIELD " + field.toGenericString());
            }
        }
        for (var nested : type.getDeclaredClasses()) {
            if (Modifier.isPublic(nested.getModifiers())) {
                lines.add("NESTED " + nested.toGenericString());
            }
        }
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
