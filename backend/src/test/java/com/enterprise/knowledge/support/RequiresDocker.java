package com.enterprise.knowledge.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips a test class when no Docker daemon can be reached.
 *
 * This is an extension rather than an {@code @EnabledIf} annotation on the base
 * class because JUnit does not apply {@code @EnabledIf} to subclasses: with the
 * annotation on {@link AbstractIntegrationTest} the subclasses ran anyway and
 * failed with a Testcontainers error. Extensions registered with
 * {@code @ExtendWith} are inherited, so this covers every subclass.
 *
 * The availability result is computed once; asking Docker for each class would
 * repeat a slow failed connection attempt.
 */
public final class RequiresDocker implements ExecutionCondition {

    private static final boolean AVAILABLE = DockerAvailability.isDockerAvailable();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return AVAILABLE
                ? ConditionEvaluationResult.enabled("A Docker daemon is available")
                : ConditionEvaluationResult.disabled(
                        "No Docker daemon is available, so PostgreSQL with pgvector cannot be started");
    }
}
