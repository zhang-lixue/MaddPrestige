package net.maddkraft.maddprestige.api.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Set;
import net.maddkraft.maddprestige.api.annotation.Stable;
import net.maddkraft.maddprestige.api.event.ConfigAppliedSnapshot;
import net.maddkraft.maddprestige.api.event.OperationEventSnapshot;
import net.maddkraft.maddprestige.api.event.ProviderHealthChangedSnapshot;
import net.maddkraft.maddprestige.api.id.ConfigRevisionId;
import net.maddkraft.maddprestige.api.id.MetricId;
import net.maddkraft.maddprestige.api.id.OperationId;
import net.maddkraft.maddprestige.api.id.ProviderId;
import net.maddkraft.maddprestige.api.id.StageId;
import net.maddkraft.maddprestige.api.id.StringIdentifier;
import net.maddkraft.maddprestige.api.metric.MetricMonotonicity;
import net.maddkraft.maddprestige.api.metric.MetricOperator;
import net.maddkraft.maddprestige.api.metric.MetricReadMode;
import net.maddkraft.maddprestige.api.metric.MetricResetPolicy;
import net.maddkraft.maddprestige.api.metric.MetricValue;
import net.maddkraft.maddprestige.api.metric.MetricValueType;
import net.maddkraft.maddprestige.api.provider.ProviderCallContext;
import net.maddkraft.maddprestige.api.provider.ProviderCancellationSignal;
import net.maddkraft.maddprestige.api.provider.ProviderDeclaration;
import net.maddkraft.maddprestige.api.provider.ProviderExecutionExpectation;
import net.maddkraft.maddprestige.api.provider.ProviderHealthState;
import net.maddkraft.maddprestige.api.provider.ProviderMetadata;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDefinition;
import net.maddkraft.maddprestige.api.provider.ProviderMetricDimension;
import net.maddkraft.maddprestige.api.provider.ProviderMetricRequest;
import net.maddkraft.maddprestige.api.provider.ProviderMetricResult;
import net.maddkraft.maddprestige.api.provider.ProviderMetricStatus;
import net.maddkraft.maddprestige.api.provider.ProviderRegistrationHandle;
import net.maddkraft.maddprestige.api.provider.RequirementProvider;
import org.junit.jupiter.api.Test;

class StableApiLeakageTest {
    private static final Set<Class<?>> ALLOWLIST = Set.of(
            ConfigAppliedSnapshot.class, ConfigRevisionId.class, MaddPrestigeService.class, MetricId.class,
            CurrencyBalanceView.class, OperationEvaluation.class, OperationEvaluationStatus.class,
            MetricMonotonicity.class, MetricOperator.class, MetricReadMode.class, MetricResetPolicy.class,
            MetricValue.class, MetricValueType.class, OperationEventSnapshot.class, OperationId.class,
            OperationKind.class, OperationResult.class, OperationStatus.class, PlayerProgressSnapshot.class,
            OperationSimulationView.class,
            ProviderCallContext.class, ProviderCancellationSignal.class, ProviderDeclaration.class,
            ProviderExecutionExpectation.class, ProviderHealthChangedSnapshot.class,
            ProviderHealthState.class, ProviderId.class, ProviderMetadata.class, ProviderMetricDefinition.class,
            ProviderMetricDimension.class, ProviderMetricRequest.class, ProviderMetricResult.class,
            ProviderMetricStatus.class, ProviderRegistrationHandle.class, ProviderView.class,
            RequirementProgressStatus.class, RequirementProgressView.class, RequirementProvider.class,
            SeasonView.class, ServiceError.class, ServiceResult.class, StageId.class, StageView.class,
            StringIdentifier.class);

    @Test
    void stableSignaturesExposeOnlyAllowlistedApiTypes() {
        ALLOWLIST.forEach(type -> {
            assertTrue(type.isAnnotationPresent(Stable.class)
                            || type.getPackage().isAnnotationPresent(Stable.class),
                    () -> type.getName() + " is allowlisted without a stability declaration");
            inspect(type.getGenericSuperclass(), type);
            for (Type contract : type.getGenericInterfaces()) {
                inspect(contract, type);
            }
            for (var constructor : type.getConstructors()) {
                for (Type parameter : constructor.getGenericParameterTypes()) {
                    inspect(parameter, type);
                }
            }
            for (var method : type.getMethods()) {
                if (method.getDeclaringClass() != Object.class) {
                    inspect(method.getGenericReturnType(), type);
                    for (Type parameter : method.getGenericParameterTypes()) {
                        inspect(parameter, type);
                    }
                }
            }
        });
    }

    private static void inspect(Type candidate, Class<?> owner) {
        if (candidate instanceof Class<?> type) {
            if (type.getName().startsWith("net.maddkraft.maddprestige.api.")) {
                assertTrue(ALLOWLIST.contains(type),
                        () -> owner.getName() + " leaks non-allowlisted API type " + type.getName());
            }
            if (type.isArray()) {
                inspect(type.getComponentType(), owner);
            }
        } else if (candidate instanceof ParameterizedType parameterized) {
            inspect(parameterized.getRawType(), owner);
            for (Type argument : parameterized.getActualTypeArguments()) {
                inspect(argument, owner);
            }
        } else if (candidate instanceof GenericArrayType array) {
            inspect(array.getGenericComponentType(), owner);
        } else if (candidate instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                inspect(bound, owner);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                inspect(bound, owner);
            }
        }
    }
}
