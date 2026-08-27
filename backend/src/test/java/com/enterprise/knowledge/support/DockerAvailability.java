package com.enterprise.knowledge.support;

import org.testcontainers.DockerClientFactory;

/**
 * Reports whether a Docker daemon can be reached.
 *
 * Kept separate from {@link PostgresContainer} on purpose: loading that class
 * starts a container, so the availability check has to live somewhere that does
 * not reference it. JUnit evaluates this before the test class is initialised,
 * which is what allows the suite to skip cleanly rather than fail with a
 * confusing connection error on a machine with no Docker.
 */
public final class DockerAvailability {

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            // Any failure to even ask counts as unavailable.
            return false;
        }
    }

    private DockerAvailability() {
    }
}
