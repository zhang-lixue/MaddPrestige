package net.maddkraft.maddprestige.core.yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record YamlPath(int documentIndex, List<Segment> segments) {
    public YamlPath {
        if (documentIndex < 0) {
            throw new IllegalArgumentException("Document index cannot be negative");
        }
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    }

    public static YamlPath document(int index) {
        return new YamlPath(index, List.of());
    }

    public YamlPath key(String key) {
        ArrayList<Segment> copy = new ArrayList<>(segments);
        copy.add(new Key(Objects.requireNonNull(key, "key")));
        return new YamlPath(documentIndex, copy);
    }

    public YamlPath index(int index) {
        ArrayList<Segment> copy = new ArrayList<>(segments);
        copy.add(new Index(index));
        return new YamlPath(documentIndex, copy);
    }

    public sealed interface Segment permits Key, Index {
    }

    public record Key(String value) implements Segment {
        public Key {
            value = Objects.requireNonNull(value, "key");
        }
    }

    public record Index(int value) implements Segment {
        public Index {
            if (value < 0) {
                throw new IllegalArgumentException("Sequence index cannot be negative");
            }
        }
    }
}
