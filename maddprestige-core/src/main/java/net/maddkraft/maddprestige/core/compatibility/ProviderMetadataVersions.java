package net.maddkraft.maddprestige.core.compatibility;

import java.util.Objects;

/** Purpose-based provider contract and implementation version metadata. */
public final class ProviderMetadataVersions {
    public static final String STABLE_API = "2-stable";

    private ProviderMetadataVersions() {
    }

    public static String implementationVersion(Class<?> implementation) {
        Package implementationPackage = Objects.requireNonNull(implementation, "implementation class").getPackage();
        String version = implementationPackage == null ? null : implementationPackage.getImplementationVersion();
        return version == null || version.isBlank() ? "development" : version;
    }
}
