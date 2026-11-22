package com.rajatmalik.commitgenerator.secrets;

import com.intellij.credentialStore.CredentialAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates credential attributes through the two-argument constructor that is
 * available in every supported platform version.
 *
 * Kotlin resolves {@code CredentialAttributes(serviceName, userName)} against
 * the 2024.1 metadata to a default-arguments constructor that is deprecated
 * (and may eventually be removed) on newer platforms. Java resolves the JVM
 * constructor directly, keeping the plugin free of deprecated API usage while
 * staying binary compatible with 2024.1+.
 */
public final class CredentialAttributesFactory {

    private CredentialAttributesFactory() {
    }

    @NotNull
    public static CredentialAttributes create(@NotNull String serviceName, @Nullable String userName) {
        return new CredentialAttributes(serviceName, userName);
    }
}
