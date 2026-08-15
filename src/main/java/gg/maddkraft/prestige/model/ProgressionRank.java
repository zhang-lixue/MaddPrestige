package gg.maddkraft.prestige.model;

import java.util.Arrays;
import java.util.Optional;

public enum ProgressionRank {
    CURIOUS(0),
    ODD(1),
    MAD(2),
    UNBOUND(3);

    private final int index;

    ProgressionRank(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public boolean isFinal() {
        return this == UNBOUND;
    }

    public Optional<ProgressionRank> next() {
        return Arrays.stream(values()).filter(rank -> rank.index == index + 1).findFirst();
    }

    public static ProgressionRank parse(String value) {
        return ProgressionRank.valueOf(value.trim().toUpperCase());
    }
}
