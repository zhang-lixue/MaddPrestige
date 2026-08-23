package net.maddkraft.qualification.phase8c;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import net.maddkraft.maddprestige.api.service.MaddPrestigeService;
import net.maddkraft.maddprestige.api.service.OperationResult;
import net.maddkraft.maddprestige.api.service.OperationStatus;
import net.maddkraft.maddprestige.api.service.PlayerProgressSnapshot;
import net.maddkraft.maddprestige.api.service.ServiceResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Qualifies the populated Phase 8C migration and exact restart against the real production plugin. */
public final class Phase8CQualificationHarness extends JavaPlugin {
    private static final String PROVIDER_ID = "phase8b_harness:external";
    private static final int MAX_ATTEMPTS = 300;

    private final QualificationProvider declaration = new QualificationProvider();
    private ProviderRegistrationHandle handle;
    private MaddPrestigeService service;
    private Properties marker;
    private UUID legacyPlayer;
    private UUID priorPlayer;
    private UUID uncertainOperation;
    private int passed;
    private boolean stopping;

    @Override
    public void onEnable() {
        getServer().getServicesManager().register(
                ProviderDeclaration.class, declaration, this, ServicePriority.Normal);
        getServer().getScheduler().runTaskLater(this, this::begin, 40L);
    }

    @Override
    public void onDisable() {
        getLogger().info("PHASE8C-Q clean shutdown pass-count=" + passed);
    }

    private void begin() {
        try {
            marker = loadMarker();
            legacyPlayer = UUID.fromString(required("phase8c.legacyPlayer"));
            priorPlayer = UUID.fromString(required("player"));
            uncertainOperation = UUID.fromString(required("phase8c.uncertainOperation"));
            eventually("exact provider recomposition", () -> {
                service = getServer().getServicesManager().load(MaddPrestigeService.class);
                return service != null && handle != null && service.stages().successful()
                        && service.stages().value().orElseThrow().size() == 2
                        && service.providers().value().orElse(List.of()).stream()
                                .anyMatch(provider -> PROVIDER_ID.equals(provider.id().value()));
            }, () -> {
                if (marker.getProperty("phase8c.completed") == null) {
                    qualifyFirstBoot();
                } else {
                    qualifyRestart();
                }
            });
        } catch (Throwable failure) {
            fail("begin", failure);
        }
    }

    private void qualifyFirstBoot() {
        try {
            require("ok".equals(scalar("PRAGMA integrity_check")), "migrated DB integrity_check failed");
            require(scalarLong("SELECT MAX(version) FROM mp_schema_migrations WHERE result='APPLIED'") == 11,
                    "Paper startup did not migrate schema 10 to 11");
            require(scalarLong("SELECT COUNT(*) FROM mp_schema_migrations "
                    + "WHERE version=11 AND result='APPLIED'") == 1, "migration 11 was not applied exactly once");
            pass("schema-10 populated fixture migrated once to integrity-clean schema 11");

            Path manifest = validatedSchemaTenBackup();
            pass("SQLite-native pre-migration backup, manifest, checksum, integrity, and rehearsal validated: "
                    + manifest.getFileName());

            require("NEEDS_RECONCILIATION".equals(scalar(
                    "SELECT state FROM mp_operations WHERE operation_id=?", uncertainOperation)),
                    "uncertain operation state was reinterpreted");
            require(scalarLong("SELECT COUNT(*) FROM mp_operation_actions "
                    + "WHERE operation_id=? AND state='UNCERTAIN'", uncertainOperation) == 1,
                    "uncertain action evidence was lost");
            require(scalarLong("SELECT COUNT(*) FROM mp_recovery_events WHERE operation_id=?",
                    uncertainOperation) >= 1, "recovery evidence was lost");
            pass("operation/action/recovery uncertainty survived migration without replay");
        } catch (Throwable failure) {
            fail("first-boot persistence", failure);
            return;
        }

        await("accepted prior player", service.playerProgress(priorPlayer), prior -> {
            require(prior.stage().orElseThrow().value().equals("novice"), "prior player stage changed");
            require(prior.currentPrestige() == 1 && prior.lifetimePrestige() == 1,
                    "prior player Prestige state changed");
            pass("existing populated player stage and Prestige identity survived the historical fixture");
            await("legacy stage-only player", service.playerProgress(legacyPlayer), legacy -> {
                require(legacy.stage().orElseThrow().value().equals("novice"),
                        "legacy stage identity was not preserved");
                require(legacy.currentPrestige() == 0 && legacy.lifetimePrestige() == 0,
                        "migration 11 did not establish zero Prestige state");
                require(scalarLong("SELECT state_revision FROM mp_player_stage_state WHERE player_uuid=?",
                        legacyPlayer) == 17, "legacy state revision changed during backfill");
                pass("stage-only historical player received atomic zero Prestige state without revision loss");
                performRankUp();
            });
        });
    }

    private void performRankUp() {
        await("migrated-player rank-up", service.rankUp(legacyPlayer), operation -> {
            requireCompleted(operation, "rank-up");
            require(!operation.requestId().equals(operation.durableOperationId().orElseThrow().value()),
                    "rank-up request identity was reused as durable identity");
            pass("real rank-up completed from migrated state with distinct request/durable identities");
            await("migrated-player Prestige", service.prestige(legacyPlayer), prestige -> {
                requireCompleted(prestige, "Prestige");
                require(!prestige.requestId().equals(prestige.durableOperationId().orElseThrow().value()),
                        "Prestige request identity was reused as durable identity");
                await("post-operation state", service.playerProgress(legacyPlayer), state -> {
                    require(state.stage().orElseThrow().value().equals("novice"),
                            "Prestige did not reset migrated player to novice");
                    require(state.currentPrestige() == 1 && state.lifetimePrestige() == 1,
                            "Prestige counters are not exact after migration");
                    pass("real Prestige completed and exact migrated player state was durable");
                    sealFirstBoot(state);
                });
            });
        });
    }

    private void sealFirstBoot(PlayerProgressSnapshot state) {
        try {
            marker.setProperty("phase8c.completed", "first-boot");
            marker.setProperty("phase8c.stage", state.stage().orElseThrow().value());
            marker.setProperty("phase8c.currentPrestige", Long.toString(state.currentPrestige()));
            marker.setProperty("phase8c.lifetimePrestige", Long.toString(state.lifetimePrestige()));
            marker.setProperty("phase8c.stageRevision", Long.toString(scalarLong(
                    "SELECT state_revision FROM mp_player_stage_state WHERE player_uuid=?", legacyPlayer)));
            marker.setProperty("phase8c.prestigeRevision", Long.toString(scalarLong(
                    "SELECT state_revision FROM mp_player_prestige_state WHERE player_uuid=?", legacyPlayer)));
            marker.setProperty("phase8c.operationCount", Long.toString(scalarLong(
                    "SELECT COUNT(*) FROM mp_operations")));
            marker.setProperty("phase8c.recoveryCount", Long.toString(scalarLong(
                    "SELECT COUNT(*) FROM mp_recovery_events")));
            marker.setProperty("phase8c.backupCount", Long.toString(acceptedBackupCount()));
            storeMarker();
            pass("first-boot exact state/revision/recovery/backup evidence sealed");
            finish("PHASE8C-Q FIRST-BOOT COMPLETE pass-count=" + passed);
        } catch (Throwable failure) {
            fail("seal first boot", failure);
        }
    }

    private void qualifyRestart() {
        await("restart exact state", service.playerProgress(legacyPlayer), state -> {
            require(state.stage().orElseThrow().value().equals(required("phase8c.stage")),
                    "restart stage changed");
            require(state.currentPrestige() == Long.parseLong(required("phase8c.currentPrestige"))
                    && state.lifetimePrestige() == Long.parseLong(required("phase8c.lifetimePrestige")),
                    "restart Prestige counters changed");
            require(scalarLong("SELECT state_revision FROM mp_player_stage_state WHERE player_uuid=?",
                    legacyPlayer) == Long.parseLong(required("phase8c.stageRevision")),
                    "restart stage revision changed");
            require(scalarLong("SELECT state_revision FROM mp_player_prestige_state WHERE player_uuid=?",
                    legacyPlayer) == Long.parseLong(required("phase8c.prestigeRevision")),
                    "restart Prestige revision changed");
            pass("unchanged restart recovered exact migrated stage/Prestige state and revisions");
            try {
                require("ok".equals(scalar("PRAGMA integrity_check")), "restart DB integrity failed");
                require(scalarLong("SELECT COUNT(*) FROM mp_operations")
                        == Long.parseLong(required("phase8c.operationCount")), "restart operation journal changed");
                require(scalarLong("SELECT COUNT(*) FROM mp_recovery_events")
                        == Long.parseLong(required("phase8c.recoveryCount")), "restart recovery evidence changed");
                require(acceptedBackupCount() == Long.parseLong(required("phase8c.backupCount")),
                        "unchanged restart created or deleted a migration backup");
                validatedSchemaTenBackup();
                pass("unchanged restart retained integrity, journal/recovery evidence, and accepted backup");
                pass("clean second-boot shutdown qualified");
                finish("PHASE8C-Q COMPLETE two-boot production qualification pass-count=" + passed);
            } catch (Throwable failure) {
                fail("restart persistence", failure);
            }
        });
    }

    private Path validatedSchemaTenBackup() throws Exception {
        Path backupDirectory = productionData().resolve("backups");
        try (var manifests = Files.list(backupDirectory)) {
            for (Path manifest : manifests.filter(path -> path.getFileName().toString().endsWith(".manifest"))
                    .sorted().toList()) {
                String content = Files.readString(manifest);
                if (content.contains("sourceSchemaVersion=10\n")
                        && content.contains("validationResult=PASS\n")
                        && content.contains("restoreRehearsalResult=PASS\n")) {
                    String name = manifest.getFileName().toString();
                    Path database = manifest.resolveSibling(name.substring(0, name.length() - ".manifest".length()));
                    validateAcceptedBackupReflectively(database);
                    return manifest;
                }
            }
        }
        throw new IllegalStateException("No accepted source-schema-10 backup manifest exists");
    }

    private void validateAcceptedBackupReflectively(Path database) throws Exception {
        ClassLoader loader = productionPlugin().getClass().getClassLoader();
        Class<?> migrationsType = Class.forName(
                "net.maddkraft.maddprestige.persistence.sqlite.SqliteMigrations", true, loader);
        Object migrations = migrationsType.getMethod("phaseEightC").invoke(null);
        Class<?> backupType = Class.forName(
                "net.maddkraft.maddprestige.persistence.sqlite.SqliteBackupService", true, loader);
        try {
            backupType.getMethod("validateAcceptedBackup", Path.class, List.class)
                    .invoke(null, database, migrations);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Independent accepted-backup validation failed", exception.getCause());
        }
    }

    private Connection databaseConnection() throws Exception {
        ClassLoader loader = productionPlugin().getClass().getClassLoader();
        Class<?> foundationType = Class.forName(
                "net.maddkraft.maddprestige.persistence.sqlite.SqliteFoundation", true, loader);
        Object foundation = foundationType.getConstructor(Path.class)
                .newInstance(productionData().resolve("maddprestige-v2.sqlite"));
        Method open = foundationType.getMethod("open");
        return (Connection) open.invoke(foundation);
    }

    private String scalar(String sql) {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet row = statement.executeQuery()) {
            require(row.next(), "diagnostic query returned no row");
            return row.getString(1);
        } catch (Exception exception) {
            throw new IllegalStateException("DB diagnostic failed: " + sql, exception);
        }
    }

    private long scalarLong(String sql) {
        return Long.parseLong(scalar(sql));
    }

    private String scalar(String sql, UUID identity) {
        try (Connection connection = databaseConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identity.toString());
            try (ResultSet row = statement.executeQuery()) {
                require(row.next(), "diagnostic query returned no row");
                return row.getString(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Bound DB diagnostic failed", exception);
        }
    }

    private long scalarLong(String sql, UUID identity) {
        return Long.parseLong(scalar(sql, identity));
    }

    private long acceptedBackupCount() throws Exception {
        try (var files = Files.list(productionData().resolve("backups"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sqlite.manifest")).count();
        }
    }

    private Path productionData() {
        return productionPlugin().getDataFolder().toPath().toAbsolutePath().normalize();
    }

    private org.bukkit.plugin.Plugin productionPlugin() {
        org.bukkit.plugin.Plugin plugin = getServer().getPluginManager().getPlugin("MaddPrestige");
        if (plugin == null) {
            throw new IllegalStateException("Production MaddPrestige plugin is absent");
        }
        return plugin;
    }

    private <T> void await(String label, CompletionStage<ServiceResult<T>> stage, Consumer<T> success) {
        stage.whenComplete((result, failure) -> getServer().getScheduler().runTask(this, () -> {
            if (failure != null) {
                fail(label, failure);
                return;
            }
            try {
                require(result.successful(), label + " returned service failure " + result.error());
                success.accept(result.value().orElseThrow());
            } catch (Throwable exception) {
                fail(label, exception);
            }
        }));
    }

    private void eventually(String label, BooleanSupplier condition, Runnable success) {
        eventually(label, condition, success, 0);
    }

    private void eventually(String label, BooleanSupplier condition, Runnable success, int attempt) {
        try {
            if (condition.getAsBoolean()) {
                success.run();
            } else if (attempt >= MAX_ATTEMPTS) {
                throw new IllegalStateException("Timed out waiting for " + label);
            } else {
                getServer().getScheduler().runTaskLater(this,
                        () -> eventually(label, condition, success, attempt + 1), 2L);
            }
        } catch (Throwable failure) {
            fail(label, failure);
        }
    }

    private void requireCompleted(OperationResult operation, String label) {
        require(operation.status() == OperationStatus.COMPLETED && operation.durableOperationId().isPresent(),
                label + " did not complete durably: " + operation.status());
    }

    private Properties loadMarker() throws Exception {
        Path markerPath = getDataFolder().toPath().resolve("qualification.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(markerPath)) {
            properties.load(input);
        }
        return properties;
    }

    private void storeMarker() throws Exception {
        Path markerPath = getDataFolder().toPath().resolve("qualification.properties");
        try (OutputStream output = Files.newOutputStream(markerPath)) {
            marker.store(output, "Phase 8C Paper qualification exact restart state");
        }
    }

    private String required(String name) {
        String value = marker.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing qualification property " + name);
        }
        return value;
    }

    private void pass(String message) {
        passed++;
        getLogger().info("PHASE8C-Q PASS " + passed + " " + message);
    }

    private void finish(String message) {
        if (!stopping) {
            stopping = true;
            getLogger().info(message);
            Bukkit.shutdown();
        }
    }

    private void fail(String label, Throwable failure) {
        if (!stopping) {
            stopping = true;
            getLogger().log(java.util.logging.Level.SEVERE, "PHASE8C-Q FAIL " + label, failure);
            Bukkit.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private final class QualificationProvider implements ProviderDeclaration {
        @Override
        public ProviderMetadata metadata(ProviderCallContext context) {
            verifyContext(context, false);
            return new ProviderMetadata("external", "phase8b.provider.external", "1.0.0", List.of(
                    new ProviderMetricDefinition(new MetricId("external_points"), MetricValueType.COUNT,
                            Set.of(MetricOperator.GREATER_OR_EQUAL),
                            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                            MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                            "phase8b.metric.external_points", "phase8b.metric.external_points.description",
                            "points", "authoritative"),
                    new ProviderMetricDefinition(new MetricId("external_bonus"), MetricValueType.COUNT,
                            Set.of(MetricOperator.GREATER_OR_EQUAL),
                            Set.of(MetricReadMode.CURRENT, MetricReadMode.LIFETIME), false,
                            MetricMonotonicity.MONOTONIC, MetricResetPolicy.NOT_APPLICABLE, Map.of(),
                            "phase8b.metric.external_bonus", "phase8b.metric.external_bonus.description",
                            "points", "authoritative")));
        }

        @Override
        public RequirementProvider requirements() {
            return (context, playerId, queries) -> {
                verifyContext(context, true);
                Map<net.maddkraft.maddprestige.api.provider.ProviderMetricRequest, ProviderMetricResult> results =
                        queries.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                                query -> query,
                                query -> ProviderMetricResult.available(MetricValue.count(10), Instant.now())));
                return CompletableFuture.completedFuture(results);
            };
        }

        @Override
        public void registered(ProviderRegistrationHandle registration) {
            handle = registration;
        }

        @Override
        public void unregistered() {
            handle = null;
        }

        private void verifyContext(ProviderCallContext context, boolean identified) {
            require(!Bukkit.isPrimaryThread(), "provider callback ran on Paper server thread");
            require(context.execution() == ProviderExecutionExpectation.BOUNDED_WORKER,
                    "provider callback was not bounded-worker");
            require(context.ownerNamespace().equals("phase8b_harness"),
                    "provider owner namespace changed from stored configuration authority");
            require(context.providerId().isPresent() == identified,
                    "provider callback identity lifetime was inconsistent");
            require(!context.cancellationRequested() && !context.expired(Instant.now()),
                    "provider callback began after cancellation/deadline");
        }
    }
}
