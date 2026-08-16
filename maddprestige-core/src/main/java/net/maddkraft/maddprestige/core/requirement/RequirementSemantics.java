package net.maddkraft.maddprestige.core.requirement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricValue;

/** Versioned, length-prefixed semantic encoding used by baseline and latch identity. */
public final class RequirementSemantics {
    public static final String FORMAT = "rsf2";

    private RequirementSemantics() {
    }

    public static String fingerprint(
            ProviderId providerId,
            MetricId metricId,
            MetricOperator operator,
            RequirementTarget target,
            MeasurementScope scope,
            CompletionMode completionMode,
            ScalingProfile scaling,
            CatchUpProfile catchUp,
            Map<String, String> filters) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                field(output, "format", FORMAT);
                field(output, "provider", providerId.value());
                field(output, "metric", metricId.value());
                field(output, "operator", operator.name());
                metricValue(output, "target.lower", target.lower());
                output.writeBoolean(target.upper().isPresent());
                if (target.upper().isPresent()) {
                    metricValue(output, "target.upper", target.upper().orElseThrow());
                }
                field(output, "scope", scope.name());
                field(output, "completion", completionMode.name());
                field(output, "scaling.strategy", scaling.strategy().name());
                field(output, "scaling.parameter", scaling.parameter().toString());
                field(output, "scaling.rounding", scaling.rounding().name());
                field(output, "scaling.quantum", scaling.roundingQuantum().toString());
                output.writeInt(scaling.stepMultipliers().size());
                for (var step : scaling.stepMultipliers().entrySet()) {
                    output.writeLong(step.getKey());
                    field(output, "scaling.step.multiplier", step.getValue().toString());
                }
                output.writeBoolean(catchUp.enabled());
                field(output, "catchup.start", catchUp.startThreshold().toString());
                field(output, "catchup.rate", catchUp.reductionRate().toString());
                field(output, "catchup.maximum", catchUp.maximumReduction().toString());
                output.writeBoolean(catchUp.floor().isPresent());
                if (catchUp.floor().isPresent()) {
                    field(output, "catchup.floor", catchUp.floor().orElseThrow().toString());
                }
                field(output, "catchup.rounding", catchUp.rounding().name());
                field(output, "catchup.quantum", catchUp.roundingQuantum().toString());
                var sorted = filters.entrySet().stream().sorted(Comparator
                        .comparing(Map.Entry<String, String>::getKey).thenComparing(Map.Entry::getValue)).toList();
                output.writeInt(sorted.size());
                for (var entry : sorted) {
                    field(output, "filter.key", entry.getKey());
                    field(output, "filter.value", entry.getValue());
                }
            }
            return FORMAT + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode requirement semantics", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void metricValue(DataOutputStream output, String label, MetricValue value) throws IOException {
        field(output, label + ".type", value.type().name());
        field(output, label + ".value", value.canonical());
    }

    private static void field(DataOutputStream output, String label, String value) throws IOException {
        bytes(output, label.getBytes(StandardCharsets.UTF_8));
        bytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void bytes(DataOutputStream output, byte[] value) throws IOException {
        output.writeInt(value.length);
        output.write(value);
    }
}
