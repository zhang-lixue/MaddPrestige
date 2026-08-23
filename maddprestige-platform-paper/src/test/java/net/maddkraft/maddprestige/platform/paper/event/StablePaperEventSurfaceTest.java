package net.maddkraft.maddprestige.platform.paper.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.maddkraft.maddprestige.api.annotation.Stable;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StablePaperEventSurfaceTest {
    private static final String EVENT_PACKAGE = "net.maddkraft.maddprestige.platform.paper.event";
    private static final Set<Class<?>> EXPECTED = Set.of(
            ConfigAppliedEvent.class,
            PostPrestigeEvent.class,
            PostRankUpEvent.class,
            PrePrestigeEvent.class,
            PreRankUpEvent.class,
            ProviderHealthChangedEvent.class);
    private static final Set<Class<?>> BUKKIT_EVENT_API = Set.of(Event.class, Cancellable.class, HandlerList.class);

    @Test
    @DisplayName("[OR8B-13] Every public Stable Paper event is inventoried and leaks only Stable SDK or event API")
    void everyStablePaperEventHasAnExactBoundedSignatureSurface() throws Exception {
        Set<Class<?>> discovered = discoverStablePaperEvents();
        assertEquals(EXPECTED, discovered, "Paper Stable additions require inventory and leakage review");

        discovered.forEach(type -> {
            inspect(type.getGenericSuperclass(), type);
            for (Type contract : type.getGenericInterfaces()) {
                inspect(contract, type);
            }
            for (var constructor : type.getConstructors()) {
                for (Type parameter : constructor.getGenericParameterTypes()) {
                    inspect(parameter, type);
                }
            }
            for (var method : type.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    inspect(method.getGenericReturnType(), type);
                    for (Type parameter : method.getGenericParameterTypes()) {
                        inspect(parameter, type);
                    }
                }
            }
        });
    }

    private static Set<Class<?>> discoverStablePaperEvents() throws Exception {
        String resourceName = EVENT_PACKAGE.replace('.', '/');
        var resources = StablePaperEventSurfaceTest.class.getClassLoader().getResources(resourceName);
        LinkedHashSet<Class<?>> discovered = new LinkedHashSet<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if (!"file".equals(resource.getProtocol())) {
                continue;
            }
            try (var files = Files.list(Path.of(resource.toURI()))) {
                discovered.addAll(files.filter(path -> path.getFileName().toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().contains("$"))
                        .map(path -> load(EVENT_PACKAGE + '.' + path.getFileName().toString()
                                .replaceFirst("\\.class$", "")))
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .filter(type -> type.isAnnotationPresent(Stable.class))
                        .collect(Collectors.toSet()));
            }
        }
        assertTrue(!discovered.isEmpty(), "Compiled Paper event package must be discoverable during verification");
        return Set.copyOf(discovered);
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, StablePaperEventSurfaceTest.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void inspect(Type candidate, Class<?> owner) {
        if (candidate instanceof Class<?> type) {
            if (type.isArray()) {
                inspect(type.getComponentType(), owner);
                return;
            }
            if (type.getName().startsWith("net.maddkraft.maddprestige.")) {
                assertTrue(type.getName().startsWith("net.maddkraft.maddprestige.api.")
                                && (type.isAnnotationPresent(Stable.class)
                                || type.getPackage().isAnnotationPresent(Stable.class)),
                        () -> owner.getName() + " leaks non-Stable implementation type " + type.getName());
            }
            if (type.getName().startsWith("org.bukkit.")) {
                assertTrue(BUKKIT_EVENT_API.contains(type),
                        () -> owner.getName() + " leaks unrelated Bukkit type " + type.getName());
            }
            assertTrue(!type.getName().startsWith("java.sql.")
                            && !type.getName().startsWith("com.zaxxer.")
                            && !type.getName().startsWith("net.luckperms.")
                            && !type.getName().startsWith("me.clip."),
                    () -> owner.getName() + " leaks persistence or optional-vendor type " + type.getName());
        } else if (candidate instanceof ParameterizedType parameterized) {
            inspect(parameterized.getRawType(), owner);
            for (Type argument : parameterized.getActualTypeArguments()) {
                inspect(argument, owner);
            }
        } else if (candidate instanceof GenericArrayType array) {
            inspect(array.getGenericComponentType(), owner);
        } else if (candidate instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                inspect(bound, owner);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                inspect(bound, owner);
            }
        }
    }
}
