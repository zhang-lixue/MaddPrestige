package net.maddkraft.maddprestige.core.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public final class RevisionHasher {
    private RevisionHasher() {
    }

    public static ContentHash hashDocuments(Map<String, String> documents) {
        Objects.requireNonNull(documents, "documents");
        MessageDigest digest = sha256();
        documents.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            updateLengthPrefixed(digest, entry.getKey().getBytes(StandardCharsets.UTF_8));
            updateLengthPrefixed(digest, entry.getValue().getBytes(StandardCharsets.UTF_8));
        });
        return new ContentHash(HexFormat.of().formatHex(digest.digest()));
    }

    public static ContentHash hashText(String text) {
        MessageDigest digest = sha256();
        return new ContentHash(HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8))));
    }

    private static void updateLengthPrefixed(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }
}
