package net.maddkraft.maddprestige.core.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

public final class LosslessYamlDocument {
    private static final int MAX_CODE_POINTS = 4 * 1024 * 1024;
    private static final Pattern SAFE_PLAIN_STRING = Pattern.compile("[A-Za-z_][A-Za-z0-9_./-]*");
    private static final Pattern YAML_TYPED_PLAIN_STRING = Pattern.compile("(?i:true|false|null)");
    private static final LoadSettings SETTINGS = LoadSettings.builder()
            .setLabel("MaddPrestige configuration")
            .setParseComments(true)
            .setUseMarks(true)
            .setAllowDuplicateKeys(false)
            .setAllowRecursiveKeys(false)
            .setAllowNonScalarKeys(false)
            .setMaxAliasesForCollections(50)
            .setCodePointLimit(MAX_CODE_POINTS)
            .build();

    private final String source;
    private final List<Node> documents;

    private LosslessYamlDocument(String source, List<Node> documents) {
        this.source = source;
        this.documents = List.copyOf(documents);
    }

    public static LosslessYamlDocument parse(String source) {
        Objects.requireNonNull(source, "source");
        ArrayList<Node> documents = new ArrayList<>();
        new Compose(SETTINGS).composeAllFromString(source).forEach(documents::add);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("YAML stream contains no documents");
        }
        return new LosslessYamlDocument(source, documents);
    }

    public int documentCount() {
        return documents.size();
    }

    public String render() {
        return source;
    }

    public String scalar(YamlPath path) {
        return requireScalar(path).getValue();
    }

    public LosslessYamlDocument replaceString(YamlPath path, String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        ScalarNode node = requireScalar(path);
        String encoded = encodeString(node.getScalarStyle(), replacement);
        return replaceRange(node, encoded);
    }

    public LosslessYamlDocument replaceBoolean(YamlPath path, boolean replacement) {
        return replaceRange(requireScalar(path), Boolean.toString(replacement));
    }

    public LosslessYamlDocument replaceDecimal(YamlPath path, String canonicalDecimal) {
        Objects.requireNonNull(canonicalDecimal, "decimal");
        if (!canonicalDecimal.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Not a canonical plain decimal: " + canonicalDecimal);
        }
        return replaceRange(requireScalar(path), canonicalDecimal);
    }

    private LosslessYamlDocument replaceRange(ScalarNode node, String encoded) {
        Mark startMark = node.getStartMark().orElseThrow(() -> new IllegalStateException("Scalar has no start mark"));
        Mark endMark = node.getEndMark().orElseThrow(() -> new IllegalStateException("Scalar has no end mark"));
        int start = utf16Offset(startMark);
        int end = utf16Offset(endMark);
        if (start < 0 || end < start || end > source.length()) {
            throw new IllegalStateException("SnakeYAML returned an invalid scalar source range");
        }
        requireSurgicallyEditable(node, start, end);
        String updated = source.substring(0, start) + encoded + source.substring(end);
        return parse(updated);
    }

    private int utf16Offset(Mark mark) {
        int codePointIndex = mark.getIndex();
        int codePointCount = source.codePointCount(0, source.length());
        if (codePointIndex < 0 || codePointIndex > codePointCount) {
            throw new IllegalStateException("SnakeYAML returned a code-point index outside the source");
        }
        return source.offsetByCodePoints(0, codePointIndex);
    }

    private void requireSurgicallyEditable(ScalarNode node, int start, int end) {
        if (node.getScalarStyle() == ScalarStyle.LITERAL || node.getScalarStyle() == ScalarStyle.FOLDED) {
            throw new IllegalArgumentException(
                    "Phase 1 lossless edits intentionally reject block scalars; their original text still round-trips unchanged");
        }
        if (node.getAnchor().isPresent()) {
            throw new IllegalArgumentException(
                    "Phase 1 lossless edits reject anchored scalars to avoid changing alias semantics");
        }
        if (!node.getTag().getValue().startsWith(Tag.PREFIX)) {
            throw new IllegalArgumentException(
                    "Phase 1 lossless edits reject custom-tagged scalars to avoid changing tag semantics");
        }
        if (source.substring(start, end).stripLeading().startsWith("!")) {
            throw new IllegalArgumentException(
                    "Phase 1 lossless edits reject explicitly tagged scalars to preserve tag presentation");
        }
    }

    private ScalarNode requireScalar(YamlPath path) {
        Objects.requireNonNull(path, "path");
        if (path.documentIndex() >= documents.size()) {
            throw new IllegalArgumentException("YAML document index is out of range: " + path.documentIndex());
        }
        Node current = documents.get(path.documentIndex());
        for (YamlPath.Segment segment : path.segments()) {
            if (segment instanceof YamlPath.Key key) {
                current = mappingValue(current, key.value());
            } else if (segment instanceof YamlPath.Index index) {
                current = sequenceValue(current, index.value());
            }
        }
        if (!(current instanceof ScalarNode scalarNode)) {
            throw new IllegalArgumentException("YAML path does not reference a scalar: " + path);
        }
        return scalarNode;
    }

    private static Node mappingValue(Node node, String key) {
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path expected a mapping before key: " + key);
        }
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode scalarKey && scalarKey.getValue().equals(key)) {
                return tuple.getValueNode();
            }
        }
        throw new IllegalArgumentException("YAML key does not exist: " + key);
    }

    private static Node sequenceValue(Node node, int index) {
        if (!(node instanceof SequenceNode sequence)) {
            throw new IllegalArgumentException("YAML path expected a sequence before index: " + index);
        }
        if (index >= sequence.getValue().size()) {
            throw new IllegalArgumentException("YAML sequence index is out of range: " + index);
        }
        return sequence.getValue().get(index);
    }

    private static String encodeString(ScalarStyle originalStyle, String replacement) {
        return switch (originalStyle) {
            case SINGLE_QUOTED -> "'" + replacement.replace("'", "''") + "'";
            case DOUBLE_QUOTED, JSON_SCALAR_STYLE -> doubleQuote(replacement);
            case PLAIN -> isUnambiguousPlainString(replacement) ? replacement : doubleQuote(replacement);
            case LITERAL, FOLDED -> throw new IllegalArgumentException(
                    "Phase 1 lossless edits intentionally reject block scalars; their original text still round-trips unchanged");
        };
    }

    private static boolean isUnambiguousPlainString(String replacement) {
        return SAFE_PLAIN_STRING.matcher(replacement).matches()
                && !YAML_TYPED_PLAIN_STRING.matcher(replacement).matches();
    }

    private static String doubleQuote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\0' -> result.append("\\0");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(character);
            }
        }
        return result.append('"').toString();
    }
}
