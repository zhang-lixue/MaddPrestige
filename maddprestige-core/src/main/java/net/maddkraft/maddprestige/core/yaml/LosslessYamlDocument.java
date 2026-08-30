package net.maddkraft.maddprestige.core.yaml;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public boolean contains(YamlPath path) {
        try {
            requireNode(path);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public LosslessYamlDocument replaceString(YamlPath path, String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        ScalarNode node = requireScalar(path);
        String encoded = encodeString(node.getScalarStyle(), replacement);
        return replaceRange(node, encoded);
    }

    public LosslessYamlDocument setString(YamlPath path, String replacement) {
        Objects.requireNonNull(replacement, "replacement");
        return contains(path) ? replaceString(path, replacement)
                : materializeScalar(path, encodeString(ScalarStyle.PLAIN, replacement));
    }

    public LosslessYamlDocument replaceBoolean(YamlPath path, boolean replacement) {
        return replaceRange(requireScalar(path), Boolean.toString(replacement));
    }

    public LosslessYamlDocument setBoolean(YamlPath path, boolean replacement) {
        return contains(path) ? replaceBoolean(path, replacement)
                : materializeScalar(path, Boolean.toString(replacement));
    }

    public LosslessYamlDocument replaceDecimal(YamlPath path, String canonicalDecimal) {
        Objects.requireNonNull(canonicalDecimal, "decimal");
        if (!canonicalDecimal.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Not a canonical plain decimal: " + canonicalDecimal);
        }
        return replaceRange(requireScalar(path), canonicalDecimal);
    }

    public LosslessYamlDocument setDecimal(YamlPath path, String canonicalDecimal) {
        Objects.requireNonNull(canonicalDecimal, "decimal");
        if (!canonicalDecimal.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Not a canonical plain decimal: " + canonicalDecimal);
        }
        return contains(path) ? replaceDecimal(path, canonicalDecimal) : materializeScalar(path, canonicalDecimal);
    }

    public List<String> sequenceScalars(YamlPath path) {
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence)) {
            throw new IllegalArgumentException("YAML path does not reference a sequence: " + path);
        }
        return sequence.getValue().stream().map(value -> {
            if (!(value instanceof ScalarNode scalar)) {
                throw new IllegalArgumentException("Sequence contains a structured value: " + path);
            }
            return scalar.getValue();
        }).toList();
    }

    public List<String> mappingKeys(YamlPath path) {
        Node node = requireNode(path);
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path does not reference a mapping: " + path);
        }
        return mapping.getValue().stream().map(tuple -> {
            if (!(tuple.getKeyNode() instanceof ScalarNode scalar)) {
                throw new IllegalArgumentException("Mapping contains a non-scalar key: " + path);
            }
            return scalar.getValue();
        }).toList();
    }

    public LosslessYamlDocument appendSequenceString(YamlPath path, String value) {
        Objects.requireNonNull(value, "value");
        String encoded = encodeString(ScalarStyle.PLAIN, value);
        if (!contains(path)) {
            return materializeSequence(path, encoded);
        }
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence)) {
            throw new IllegalArgumentException("YAML path does not reference a sequence: " + path);
        }
        if (sequenceScalars(path).contains(value)) {
            throw new IllegalArgumentException("Sequence already contains value: " + value);
        }
        if (sequence.getValue().isEmpty()) {
            return expandEmptyCollection(node, " ".repeat(parentIndent(node) + 2) + "- " + encoded);
        }
        Node last = sequence.getValue().getLast();
        int insertion = nodeLineEnd(last);
        int indent = lineIndent(utf16Offset(last.getStartMark().orElseThrow()));
        return insert(insertion, " ".repeat(indent) + "- " + encoded + lineEnding());
    }

    public LosslessYamlDocument removeSequenceString(YamlPath path, String value) {
        Objects.requireNonNull(value, "value");
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence)) {
            throw new IllegalArgumentException("YAML path does not reference a sequence: " + path);
        }
        for (Node item : sequence.getValue()) {
            if (item instanceof ScalarNode scalar && scalar.getValue().equals(value)) {
                if (sequence.getValue().size() == 1) {
                    return emptyCollection(sequence, "[]");
                }
                return removeLines(item);
            }
        }
        throw new IllegalArgumentException("Sequence does not contain value: " + value);
    }

    public LosslessYamlDocument appendSequenceStructure(YamlPath path, Map<String, ?> value) {
        Objects.requireNonNull(value, "structured sequence value");
        if (!contains(path)) {
            return materialize(path, (indent, key) -> " ".repeat(indent) + key + ":" + lineEnding()
                    + sequenceStructure(indent + 2, value));
        }
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence)) {
            throw new IllegalArgumentException("YAML path does not reference a sequence: " + path);
        }
        if (sequence.getValue().isEmpty()) {
            return expandEmptyCollection(node, sequenceStructure(parentIndent(node) + 2, value));
        }
        Node last = sequence.getValue().getLast();
        int indent = lineIndent(utf16Offset(last.getStartMark().orElseThrow()));
        return insert(nodeLineEnd(last), sequenceStructure(indent, value) + lineEnding());
    }

    public LosslessYamlDocument replaceSequenceStructure(YamlPath path, int index, Map<String, ?> value) {
        Objects.requireNonNull(value, "structured sequence value");
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence) || index < 0 || index >= sequence.getValue().size()) {
            throw new IllegalArgumentException("YAML sequence index is out of range: " + index);
        }
        Node item = sequence.getValue().get(index);
        int start = sequenceItemStart(item);
        int end = index + 1 < sequence.getValue().size()
                ? lineStart(utf16Offset(sequence.getValue().get(index + 1).getStartMark().orElseThrow()))
                : nodeLineEnd(item);
        return replaceRange(start, end, sequenceStructure(lineIndent(start), value) + lineEnding());
    }

    public LosslessYamlDocument removeSequenceIndex(YamlPath path, int index) {
        Node node = requireNode(path);
        if (!(node instanceof SequenceNode sequence) || index < 0 || index >= sequence.getValue().size()) {
            throw new IllegalArgumentException("YAML sequence index is out of range: " + index);
        }
        if (sequence.getValue().size() == 1) {
            return emptyCollection(sequence, "[]");
        }
        Node item = sequence.getValue().get(index);
        int start = sequenceItemStart(item);
        int end = index + 1 < sequence.getValue().size()
                ? lineStart(utf16Offset(sequence.getValue().get(index + 1).getStartMark().orElseThrow()))
                : nodeLineEnd(item);
        return replaceRange(start, end, "");
    }

    public LosslessYamlDocument appendMappingBlock(YamlPath path, String key, List<String> valueLines) {
        Objects.requireNonNull(key, "key");
        valueLines = List.copyOf(Objects.requireNonNull(valueLines, "value lines"));
        if (valueLines.isEmpty() || valueLines.stream().anyMatch(line -> line.isBlank() || line.contains("\r")
                || line.contains("\n"))) {
            throw new IllegalArgumentException("Mapping block lines must be nonblank single lines");
        }
        Node node = requireNode(path);
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path does not reference a mapping: " + path);
        }
        if (mappingKeys(path).contains(key)) {
            throw new IllegalArgumentException("Mapping already contains key: " + key);
        }
        String encodedKey = encodeString(ScalarStyle.PLAIN, key);
        int indent;
        int insertion;
        if (mapping.getValue().isEmpty()) {
            indent = parentIndent(node) + 2;
            return expandEmptyCollection(node, mappingBlock(indent, encodedKey, valueLines));
        }
        NodeTuple last = mapping.getValue().getLast();
        indent = last.getKeyNode().getStartMark().orElseThrow().getColumn();
        insertion = nodeLineEnd(last.getValueNode());
        return insert(insertion, mappingBlock(indent, encodedKey, valueLines) + lineEnding());
    }

    public LosslessYamlDocument appendMappingStructure(YamlPath path, String key, Map<String, ?> value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "structured mapping value");
        String encodedKey = encodeString(ScalarStyle.PLAIN, key);
        if (!contains(path)) {
            return materialize(path, (indent, owner) -> " ".repeat(indent) + owner + ":" + lineEnding()
                    + mappingStructure(indent + 2, encodedKey, value));
        }
        Node node = requireNode(path);
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path does not reference a mapping: " + path);
        }
        if (mappingKeys(path).contains(key)) {
            throw new IllegalArgumentException("Mapping already contains key: " + key);
        }
        if (mapping.getValue().isEmpty()) {
            return expandEmptyCollection(node, mappingStructure(parentIndent(node) + 2, encodedKey, value));
        }
        NodeTuple last = mapping.getValue().getLast();
        int indent = last.getKeyNode().getStartMark().orElseThrow().getColumn();
        return insert(nodeLineEnd(last.getValueNode()), mappingStructure(indent, encodedKey, value) + lineEnding());
    }

    public LosslessYamlDocument replaceMappingStructure(
            YamlPath path,
            String key,
            Map<String, ?> value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "structured mapping value");
        Node node = requireNode(path);
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path does not reference a mapping: " + path);
        }
        for (int index = 0; index < mapping.getValue().size(); index++) {
            NodeTuple tuple = mapping.getValue().get(index);
            if (tuple.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) {
                int start = lineStart(utf16Offset(tuple.getKeyNode().getStartMark().orElseThrow()));
                int end = index + 1 < mapping.getValue().size()
                        ? lineStart(utf16Offset(mapping.getValue().get(index + 1).getKeyNode()
                                .getStartMark().orElseThrow()))
                        : nodeLineEnd(tuple.getValueNode());
                return replaceRange(start, end, mappingStructure(lineIndent(start),
                        encodeString(ScalarStyle.PLAIN, key), value) + lineEnding());
            }
        }
        throw new IllegalArgumentException("Mapping does not contain key: " + key);
    }

    public LosslessYamlDocument removeMappingEntry(YamlPath path, String key) {
        Objects.requireNonNull(key, "key");
        Node node = requireNode(path);
        if (!(node instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("YAML path does not reference a mapping: " + path);
        }
        for (int index = 0; index < mapping.getValue().size(); index++) {
            NodeTuple tuple = mapping.getValue().get(index);
            if (tuple.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) {
                if (mapping.getValue().size() == 1) {
                    return emptyCollection(mapping, "{}");
                }
                int start = lineStart(utf16Offset(tuple.getKeyNode().getStartMark().orElseThrow()));
                int end = index + 1 < mapping.getValue().size()
                        ? lineStart(utf16Offset(mapping.getValue().get(index + 1).getKeyNode()
                                .getStartMark().orElseThrow()))
                        : nodeLineEnd(tuple.getValueNode());
                return replaceRange(start, end, "");
            }
        }
        throw new IllegalArgumentException("Mapping does not contain key: " + key);
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
        return replaceRange(start, end, encoded);
    }

    private LosslessYamlDocument expandEmptyCollection(Node node, String block) {
        int start = utf16Offset(node.getStartMark().orElseThrow());
        int end = utf16Offset(node.getEndMark().orElseThrow());
        int newline = source.indexOf('\n', end);
        int lineContentEnd = newline < 0 ? source.length()
                : newline > 0 && source.charAt(newline - 1) == '\r' ? newline - 1 : newline;
        String suffix = source.substring(end, lineContentEnd);
        if (!suffix.isBlank() && !suffix.stripLeading().startsWith("#")) {
            throw new IllegalArgumentException("Empty collection has unsupported trailing syntax");
        }
        String preservedComment = suffix.isBlank() ? "" : suffix;
        return replaceRange(start, lineContentEnd, preservedComment + lineEnding() + block);
    }

    private LosslessYamlDocument emptyCollection(Node node, String emptyFlow) {
        int start = utf16Offset(node.getStartMark().orElseThrow());
        int end = utf16Offset(node.getEndMark().orElseThrow());
        if (start < source.length() && (source.charAt(start) == '[' || source.charAt(start) == '{')) {
            return replaceRange(start, end, emptyFlow);
        }
        int firstChildLine = lineStart(start);
        if (firstChildLine == 0) {
            throw new IllegalArgumentException("Block collection has no owning mapping line");
        }
        int ownerStart = lineStart(firstChildLine - 1);
        int ownerEnd = firstChildLine;
        String owner = source.substring(ownerStart, ownerEnd);
        int contentEnd = owner.endsWith("\r\n") ? owner.length() - 2
                : owner.endsWith("\n") ? owner.length() - 1 : owner.length();
        String content = owner.substring(0, contentEnd);
        int comment = content.indexOf('#');
        int searchEnd = comment < 0 ? content.length() : comment;
        int colon = content.lastIndexOf(':', Math.max(0, searchEnd - 1));
        if (colon < 0 || !content.substring(colon + 1, searchEnd).isBlank()) {
            throw new IllegalArgumentException("Block collection owning line is not surgically editable");
        }
        String preservedComment = comment < 0 ? "" : " " + content.substring(comment).stripLeading();
        String replacement = content.substring(0, colon + 1) + " " + emptyFlow + preservedComment + lineEnding();
        return replaceRange(ownerStart, nodeLineEnd(node), replacement);
    }

    private LosslessYamlDocument removeLines(Node node) {
        return replaceRange(lineStart(utf16Offset(node.getStartMark().orElseThrow())), nodeLineEnd(node), "");
    }

    private int sequenceItemStart(Node item) {
        int childLine = lineStart(utf16Offset(item.getStartMark().orElseThrow()));
        if (childLine == 0) {
            return childLine;
        }
        int precedingLine = lineStart(childLine - 1);
        if (source.substring(precedingLine, childLine).strip().equals("-")) {
            return precedingLine;
        }
        return childLine;
    }

    private LosslessYamlDocument insert(int offset, String value) {
        return replaceRange(offset, offset, value);
    }

    private String mappingStructure(int indent, String encodedKey, Map<String, ?> value) {
        StringBuilder result = new StringBuilder(" ".repeat(indent)).append(encodedKey).append(':');
        if (value.isEmpty()) {
            return result.append(" {}").toString();
        }
        result.append(lineEnding());
        appendMapping(result, value, indent + 2);
        return stripTrailingLineEnding(result.toString());
    }

    private String sequenceStructure(int indent, Map<String, ?> value) {
        StringBuilder result = new StringBuilder(" ".repeat(indent)).append('-');
        if (value.isEmpty()) {
            return result.append(" {}").toString();
        }
        result.append(lineEnding());
        appendMapping(result, value, indent + 2);
        return stripTrailingLineEnding(result.toString());
    }

    private void appendMapping(StringBuilder result, Map<String, ?> value, int indent) {
        value.forEach((key, child) -> {
            result.append(" ".repeat(indent)).append(encodeString(ScalarStyle.PLAIN, key)).append(':');
            appendStructuredValue(result, child, indent);
        });
    }

    private void appendSequence(StringBuilder result, List<?> value, int indent) {
        for (Object child : value) {
            result.append(" ".repeat(indent)).append('-');
            if (structuredScalar(child)) {
                result.append(' ').append(encodeStructuredScalar(child)).append(lineEnding());
            } else {
                result.append(lineEnding());
                appendStructuredCollection(result, child, indent + 2);
            }
        }
    }

    private void appendStructuredValue(StringBuilder result, Object value, int indent) {
        if (structuredScalar(value)) {
            result.append(' ').append(encodeStructuredScalar(value)).append(lineEnding());
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            result.append(" {}").append(lineEnding());
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            result.append(" []").append(lineEnding());
            return;
        }
        result.append(lineEnding());
        appendStructuredCollection(result, value, indent + 2);
    }

    @SuppressWarnings("unchecked")
    private void appendStructuredCollection(StringBuilder result, Object value, int indent) {
        if (value instanceof Map<?, ?> map) {
            appendMapping(result, (Map<String, ?>) map, indent);
        } else if (value instanceof List<?> list) {
            appendSequence(result, list, indent);
        } else {
            throw new IllegalArgumentException("Unsupported structured YAML value");
        }
    }

    private static boolean structuredScalar(Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }

    private static String encodeStructuredScalar(Object value) {
        if (value instanceof String text) {
            return encodeString(ScalarStyle.PLAIN, text);
        }
        if (value instanceof Boolean flag) {
            return flag.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        throw new IllegalArgumentException("Unsupported structured YAML scalar");
    }

    private String stripTrailingLineEnding(String value) {
        return value.endsWith(lineEnding()) ? value.substring(0, value.length() - lineEnding().length()) : value;
    }

    private LosslessYamlDocument materializeScalar(YamlPath path, String encoded) {
        return materialize(path, (indent, key) -> " ".repeat(indent) + key + ": " + encoded);
    }

    private LosslessYamlDocument materializeSequence(YamlPath path, String encoded) {
        return materialize(path, (indent, key) -> " ".repeat(indent) + key + ":" + lineEnding()
                + " ".repeat(indent + 2) + "- " + encoded);
    }

    private LosslessYamlDocument materialize(YamlPath path, MissingLeaf leaf) {
        Objects.requireNonNull(path, "path");
        if (path.documentIndex() >= documents.size()) {
            throw new IllegalArgumentException("YAML document index is out of range: " + path.documentIndex());
        }
        Node current = documents.get(path.documentIndex());
        for (int index = 0; index < path.segments().size(); index++) {
            YamlPath.Segment segment = path.segments().get(index);
            if (!(segment instanceof YamlPath.Key key)) {
                throw new IllegalArgumentException("Missing sequence-index paths cannot be materialized");
            }
            if (!(current instanceof MappingNode mapping)) {
                throw new IllegalArgumentException("YAML path expected a mapping before key: " + key.value());
            }
            Node next = mappingValueOrNull(mapping, key.value());
            if (next != null) {
                current = next;
                continue;
            }
            List<YamlPath.Key> remaining = new ArrayList<>();
            for (int nested = index; nested < path.segments().size(); nested++) {
                if (!(path.segments().get(nested) instanceof YamlPath.Key nestedKey)) {
                    throw new IllegalArgumentException("Missing sequence-index paths cannot be materialized");
                }
                remaining.add(nestedKey);
            }
            int indent = mapping.getValue().isEmpty() ? parentIndent(mapping) + 2
                    : mapping.getValue().getLast().getKeyNode().getStartMark().orElseThrow().getColumn();
            StringBuilder block = new StringBuilder();
            for (int nested = 0; nested < remaining.size() - 1; nested++) {
                block.append(" ".repeat(indent + nested * 2)).append(remaining.get(nested).value())
                        .append(':').append(lineEnding());
            }
            int leafIndent = indent + Math.max(0, remaining.size() - 1) * 2;
            block.append(leaf.render(leafIndent, remaining.getLast().value()));
            return appendToMapping(mapping, block.toString());
        }
        throw new IllegalArgumentException("YAML path already exists: " + path);
    }

    private LosslessYamlDocument appendToMapping(MappingNode mapping, String block) {
        if (mapping.getValue().isEmpty()) {
            return expandEmptyCollection(mapping, block);
        }
        int insertion = nodeLineEnd(mapping.getValue().getLast().getValueNode());
        return insert(insertion, block + lineEnding());
    }

    private LosslessYamlDocument replaceRange(int start, int end, String encoded) {
        if (start < 0 || end < start || end > source.length()) {
            throw new IllegalArgumentException("YAML source edit range is invalid");
        }
        return parse(source.substring(0, start) + encoded + source.substring(end));
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
        Node current = requireNode(path);
        if (!(current instanceof ScalarNode scalarNode)) {
            throw new IllegalArgumentException("YAML path does not reference a scalar: " + path);
        }
        return scalarNode;
    }

    private Node requireNode(YamlPath path) {
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
        return current;
    }

    private int parentIndent(Node node) {
        int start = utf16Offset(node.getStartMark().orElseThrow());
        int line = lineStart(start);
        int indent = 0;
        while (line + indent < source.length() && source.charAt(line + indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private int lineStart(int offset) {
        int newline = source.lastIndexOf('\n', Math.max(0, offset - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private int lineIndent(int offset) {
        int start = lineStart(offset);
        int indent = 0;
        while (start + indent < source.length() && source.charAt(start + indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private int lineEnd(int offset) {
        int newline = source.indexOf('\n', offset);
        return newline < 0 ? source.length() : newline + 1;
    }

    private int nodeLineEnd(Node node) {
        int end = utf16Offset(node.getEndMark().orElseThrow());
        return end == lineStart(end) ? end : lineEnd(end);
    }

    private String lineEnding() {
        return source.contains("\r\n") ? "\r\n" : "\n";
    }

    private String mappingBlock(int indent, String key, List<String> lines) {
        String lineEnding = lineEnding();
        StringBuilder result = new StringBuilder(" ".repeat(indent)).append(key).append(':').append(lineEnding);
        for (String line : lines) {
            result.append(" ".repeat(indent + 2)).append(line).append(lineEnding);
        }
        return result.substring(0, result.length() - lineEnding.length());
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

    private static Node mappingValueOrNull(MappingNode mapping, String key) {
        for (NodeTuple tuple : mapping.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode scalarKey && scalarKey.getValue().equals(key)) {
                return tuple.getValueNode();
            }
        }
        return null;
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

    @FunctionalInterface
    private interface MissingLeaf {
        String render(int indent, String key);
    }
}
