package com.cirserver.createciraddition.network;

import java.util.Objects;

/** CIR protocol allowlist; all other namespaces keep NeoForge's equality rule. */
public final class NetworkCompatibility {
    private NetworkCompatibility() {}

    public static boolean matches(String namespace, String left, String right) {
        if (!"create_cir_addition".equals(namespace)) {
            return Objects.equals(left, right);
        }
        return supported(left) && supported(right);
    }

    private static boolean supported(String version) {
        return "3.5.0".equals(version);
    }
}
