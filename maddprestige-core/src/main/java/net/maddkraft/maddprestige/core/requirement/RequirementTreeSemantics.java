package net.maddkraft.maddprestige.core.requirement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RequirementTreeSemantics {
    private RequirementTreeSemantics() {
    }

    static String fingerprint(RequirementNode root) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                field(output, "rtf1");
                node(output, root);
            }
            return "rtf1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not encode requirement tree semantics", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String none() {
        try {
            return "rtf1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest("rtf1:none".getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void node(DataOutputStream output, RequirementNode node) throws IOException {
        field(output, node.id().value());
        if (node instanceof RequirementLeaf leaf) {
            field(output, "leaf");
            field(output, leaf.definition().semanticFingerprint());
            return;
        }
        RequirementGroup group = (RequirementGroup) node;
        field(output, "group");
        field(output, group.mode().name());
        field(output, group.threshold().toString());
        field(output, Boolean.toString(group.catchUp().enabled()));
        field(output, group.catchUp().startThreshold().toString());
        field(output, group.catchUp().reductionRate().toString());
        field(output, group.catchUp().maximumReduction().toString());
        field(output, group.catchUp().floor().map(Object::toString).orElse(""));
        field(output, group.catchUp().rounding().name());
        field(output, group.catchUp().roundingQuantum().toString());
        output.writeInt(group.children().size());
        for (RequirementChild child : group.children()) {
            field(output, child.weight().toString());
            node(output, child.node());
        }
    }

    private static void field(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}
