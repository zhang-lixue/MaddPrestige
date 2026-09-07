package net.maddkraft.maddprestige.platform.paper.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/** Atomic UTF-8 locale catalog and injection-safe MiniMessage rendering boundary. */
public final class PaperMessageService {
    public static final String DEFAULT_LOCALE = "en_US";
    private static final String DEFAULT_RESOURCE = "locales/en_US.yml";
    private static final int MAX_CATALOG_BYTES = 1024 * 1024;
    private static final int MAX_TEMPLATE_LENGTH = 4096;
    private static final int MAX_ARGUMENT_CODE_POINTS = 512;
    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}_[A-Z]{2}");
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");
    private static final MiniMessage STRICT_MINI_MESSAGE = MiniMessage.builder().strict(true).build();
    private static final Set<String> TEMPLATE_ARGUMENTS = Set.of(
            "acknowledgement", "active", "after", "allowed", "amount", "base", "before", "blocked", "blockers",
            "canonical",
            "code",
            "completion", "component", "confirmation", "configured_cost", "configured_reward", "count", "counter",
            "cooldown_remaining", "currency", "current", "current_lifetime",
            "current_prestige", "current_stage", "deferred", "delta", "detail", "disposition", "document", "draft",
            "expires",
            "eligible_at", "errors", "findings", "formula", "group", "hash", "healthy", "id", "intended_target",
            "kind",
            "indicator", "label", "level", "lifetime", "missing",
            "locale", "metric", "metrics", "mode", "next",
            "operation", "operator", "path", "permission", "player", "prestige_status", "progress", "projected",
            "provider",
            "purpose", "rank",
            "rankup_status",
            "prestige_maximum", "previous", "reason", "remediation", "repeatability", "requirement", "revision",
            "rewards", "risk",
            "scaling", "scope", "stage",
            "source", "status", "target", "threshold", "total",
            "target_lifetime", "target_prestige", "target_stage", "topic", "type", "usage", "uuid", "value", "variant",
            "version", "warnings");
    private static final TagResolver VALIDATION_ARGUMENTS = TagResolver.resolver(TEMPLATE_ARGUMENTS.stream()
            .map(name -> Placeholder.unparsed(name, "value"))
            .toList());
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "command.failed",
            "command.error.internal",
            "gui.action.failed",
            "gui.administration.failed",
            "gui.unavailable",
            "locale.reload.failed",
            "locale.reload.permission_denied",
            "locale.reload.success",
            "locale.reload.usage",
            "command.runtime.permission_denied",
            "command.runtime.usage");
    private static final LoadSettings YAML = LoadSettings.builder()
            .setLabel("MaddPrestige locale catalog")
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(0)
            .setCodePointLimit(MAX_CATALOG_BYTES)
            .build();

    private final Path dataDirectory;
    private final Path realDataDirectory;
    private final ClassLoader classLoader;
    private final Consumer<String> diagnostics;
    private final Map<String, String> builtInFallback;
    private final AtomicReference<CatalogSnapshot> current;
    private final AtomicLong versions = new AtomicLong();

    private PaperMessageService(
            Path dataDirectory,
            ClassLoader classLoader,
            Consumer<String> diagnostics,
            Map<String, String> builtInFallback) throws IOException {
        this.dataDirectory = dataDirectory;
        rejectSymbolicComponents(dataDirectory);
        this.realDataDirectory = dataDirectory.toRealPath();
        this.classLoader = classLoader;
        this.diagnostics = diagnostics;
        this.builtInFallback = builtInFallback;
        this.current = new AtomicReference<>(new CatalogSnapshot(
                DEFAULT_LOCALE, Map.of(), builtInFallback, versions.incrementAndGet()));
    }

    public static PaperMessageService open(
            Path dataDirectory,
            ClassLoader classLoader,
            Consumer<String> diagnostics) throws IOException {
        Path normalized = Objects.requireNonNull(dataDirectory, "data directory").toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        ClassLoader loader = Objects.requireNonNull(classLoader, "class loader");
        Consumer<String> sink = Objects.requireNonNull(diagnostics, "diagnostics");
        Map<String, String> fallback = loadBundledFallback(loader);
        requireCoverage(fallback);
        PaperMessageService service = new PaperMessageService(normalized, loader, sink, fallback);
        service.prepareLayout();
        LocaleReloadResult result = service.reload();
        if (!result.successful()) {
            sink.accept("Locale configuration was rejected; built-in en_US remains active: " + result.detail());
        }
        return service;
    }

    public Set<String> requiredKeys() {
        return builtInFallback.keySet();
    }

    public String locale() {
        return current.get().locale();
    }

    public long version() {
        return current.get().version();
    }

    public synchronized LocaleReloadResult reload() {
        CatalogSnapshot previous = current.get();
        try {
            String locale = configuredLocale();
            Map<String, String> selected = selectedCatalog(locale);
            selected.forEach(PaperMessageService::validateTemplate);
            LegacyLocaleKeys.aliases().forEach((canonical, legacy) -> {
                if (selected.containsKey(canonical) && selected.containsKey(legacy)) {
                    diagnostics.accept("Locale key " + canonical
                            + " overrides its deprecated alias " + legacy + ".");
                }
            });
            CatalogSnapshot replacement = new CatalogSnapshot(
                    locale, selected, builtInFallback, versions.incrementAndGet());
            current.set(replacement);
            return new LocaleReloadResult(true, locale, "locale.reload.success",
                    "Published complete catalog version " + replacement.version());
        } catch (IOException | RuntimeException exception) {
            String detail = boundedDiagnostic(exception);
            diagnostics.accept("Locale reload rejected; catalog version " + previous.version()
                    + " remains active: " + detail);
            return new LocaleReloadResult(false, previous.locale(), "locale.reload.failed", detail);
        }
    }

    public Component render(String key) {
        return render(key, Map.of());
    }

    public Component render(MessageReference reference) {
        Objects.requireNonNull(reference, "message reference");
        return render(reference.key(), reference.arguments());
    }

    public Component render(String key, Map<String, ?> arguments) {
        String safeKey = LegacyLocaleKeys.canonicalize(requireKey(key));
        CatalogSnapshot snapshot = current.get();
        String template = snapshot.selected().get(safeKey);
        if (template == null) {
            template = LegacyLocaleKeys.legacyForCanonical(safeKey)
                    .map(snapshot.selected()::get).orElse(null);
        }
        if (template == null) {
            template = snapshot.fallback().get(safeKey);
        }
        if (template == null) {
            diagnostics.accept("Message key is absent from selected locale and en_US: " + safeKey);
            return Component.text("[message:" + safeKey + "]");
        }
        TagResolver resolver = resolver(arguments);
        try {
            return STRICT_MINI_MESSAGE.deserialize(template, resolver);
        } catch (RuntimeException exception) {
            String fallback = snapshot.fallback().get(safeKey);
            if (fallback != null && !fallback.equals(template)) {
                diagnostics.accept("Selected locale message failed to render; en_US fallback used for " + safeKey);
                try {
                    return STRICT_MINI_MESSAGE.deserialize(fallback, resolver);
                } catch (RuntimeException ignored) {
                    // The bundled catalog was validated at construction; retain a bounded visible diagnostic.
                }
            }
            diagnostics.accept("Message render failed safely for " + safeKey + ": " + boundedDiagnostic(exception));
            return Component.text("[message:" + safeKey + "]");
        }
    }

    private void prepareLayout() throws IOException {
        Path localeDirectory = dataDirectory.resolve("locales").normalize();
        requireChild(localeDirectory);
        if (Files.notExists(localeDirectory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(localeDirectory);
        }
        requireRealContainment(localeDirectory);
        Path settings = dataDirectory.resolve("locale.yml").normalize();
        requireChild(settings);
        if (Files.notExists(settings, LinkOption.NOFOLLOW_LINKS)) {
            Files.writeString(settings,
                    "# MaddPrestige V2 server-global locale\nlocale: en_US\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }

    private String configuredLocale() throws IOException {
        Path settings = dataDirectory.resolve("locale.yml").normalize();
        requireSafeRegularFile(settings);
        Map<String, String> values = parseCatalog(Files.readString(settings, StandardCharsets.UTF_8));
        if (!values.keySet().equals(Set.of("locale"))) {
            throw new IllegalArgumentException("locale.yml must contain exactly the locale key");
        }
        String locale = values.get("locale");
        if (!LOCALE.matcher(locale).matches()) {
            throw new IllegalArgumentException("Locale must use language_COUNTRY form, for example en_US");
        }
        return locale;
    }

    private Map<String, String> selectedCatalog(String locale) throws IOException {
        Path path = dataDirectory.resolve("locales").resolve(locale + ".yml").normalize();
        requireChild(path);
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Map.of();
        }
        requireSafeRegularFile(path);
        if (Files.size(path) > MAX_CATALOG_BYTES) {
            throw new IllegalArgumentException("Locale catalog exceeds the one-megabyte bound");
        }
        return parseCatalog(Files.readString(path, StandardCharsets.UTF_8));
    }

    private void requireSafeRegularFile(Path path) throws IOException {
        requireChild(path);
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Locale path is absent, non-regular, or symbolic: " + path.getFileName());
        }
        requireRealContainment(path);
        if (Files.size(path) > MAX_CATALOG_BYTES) {
            throw new IOException("Locale file exceeds the one-megabyte bound: " + path.getFileName());
        }
    }

    private void requireChild(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(dataDirectory)) {
            throw new IOException("Locale path escaped the plugin data directory");
        }
        rejectSymbolicComponents(absolute);
        Path existing = absolute;
        while (existing != null && Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null || !existing.toRealPath().startsWith(realDataDirectory)) {
            throw new IOException("Locale path resolved outside the plugin data directory");
        }
    }

    private void requireRealContainment(Path path) throws IOException {
        rejectSymbolicComponents(path);
        if (!path.toRealPath().startsWith(realDataDirectory)) {
            throw new IOException("Locale path resolved outside the plugin data directory");
        }
    }

    private static void rejectSymbolicComponents(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path element : absolute) {
            current = current == null ? element : current.resolve(element);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && isSymbolicPath(current)) {
                throw new IOException("Locale path contains a symbolic-link component: " + current.getFileName());
            }
        }
    }

    private static boolean isSymbolicPath(Path path) throws IOException {
        var attributes = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        return attributes.isSymbolicLink() || attributes.isOther();
    }

    private static Map<String, String> loadBundledFallback(ClassLoader classLoader) throws IOException {
        try (InputStream input = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) {
                throw new IOException("Bundled en_US catalog is absent");
            }
            byte[] bytes = input.readNBytes(MAX_CATALOG_BYTES + 1);
            if (bytes.length > MAX_CATALOG_BYTES) {
                throw new IOException("Bundled en_US catalog exceeds the one-megabyte bound");
            }
            Map<String, String> fallback = parseCatalog(new String(bytes, StandardCharsets.UTF_8));
            fallback.forEach(PaperMessageService::validateTemplate);
            return fallback;
        }
    }

    private static Map<String, String> parseCatalog(String source) {
        Object loaded = new Load(YAML).loadFromString(Objects.requireNonNull(source, "catalog source"));
        if (!(loaded instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("Locale catalog must contain one top-level mapping");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException("Locale keys and values must be strings");
            }
            key = requireKey(key);
            if (value.isBlank() || value.length() > MAX_TEMPLATE_LENGTH || hasForbiddenControl(value)) {
                throw new IllegalArgumentException("Locale template is blank, too long, or contains controls: " + key);
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static void requireCoverage(Map<String, String> fallback) {
        if (!fallback.keySet().containsAll(REQUIRED_KEYS)) {
            java.util.HashSet<String> missing = new java.util.HashSet<>(REQUIRED_KEYS);
            missing.removeAll(fallback.keySet());
            throw new IllegalArgumentException("Bundled en_US catalog is incomplete: " + missing);
        }
    }

    private static void validateTemplate(String key, String template) {
        try {
            STRICT_MINI_MESSAGE.deserialize(template, VALIDATION_ARGUMENTS);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed MiniMessage template for " + key + ": "
                    + boundedDiagnostic(exception), exception);
        }
    }

    private static TagResolver resolver(Map<String, ?> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.size() > 64) {
            throw new IllegalArgumentException("At most 64 message arguments are allowed");
        }
        TagResolver.Builder builder = TagResolver.builder();
        arguments.forEach((name, value) -> {
            if (!TEMPLATE_ARGUMENTS.contains(name)) {
                throw new IllegalArgumentException("Unsupported message argument: " + name);
            }
            builder.resolver(Placeholder.unparsed(name, safeArgument(value)));
        });
        return builder.build();
    }

    private static String safeArgument(Object value) {
        String source = String.valueOf(Objects.requireNonNull(value, "argument value"));
        StringBuilder result = new StringBuilder();
        int count = 0;
        for (int offset = 0; offset < source.length() && count < MAX_ARGUMENT_CODE_POINTS;) {
            int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);
            result.appendCodePoint(Character.isISOControl(codePoint) || codePoint == 0x7f ? 0xfffd : codePoint);
            count++;
        }
        if (source.codePointCount(0, source.length()) > MAX_ARGUMENT_CODE_POINTS) {
            result.append('…');
        }
        return result.toString();
    }

    private static boolean hasForbiddenControl(String value) {
        return value.codePoints().anyMatch(codePoint -> (Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t') || codePoint == 0x7f);
    }

    private static String requireKey(String key) {
        String value = Objects.requireNonNull(key, "message key");
        if (!KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid bounded message key: " + value);
        }
        return value;
    }

    private static String boundedDiagnostic(Throwable failure) {
        Throwable current = Objects.requireNonNull(failure, "failure");
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return safeArgument(current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage());
    }

    private record CatalogSnapshot(
            String locale,
            Map<String, String> selected,
            Map<String, String> fallback,
            long version) {
        private CatalogSnapshot {
            locale = Objects.requireNonNull(locale, "locale");
            selected = Map.copyOf(Objects.requireNonNull(selected, "selected catalog"));
            fallback = Map.copyOf(Objects.requireNonNull(fallback, "fallback catalog"));
            if (version < 1) {
                throw new IllegalArgumentException("Catalog version must be positive");
            }
        }
    }
}
