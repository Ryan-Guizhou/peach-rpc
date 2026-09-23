package io.peach.rpc.registry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.ServiceKey;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class RegistryContractTest {


    @Test
    void shouldRejectCapabilityAndRegistrarMismatch() {
        Registry invalid = new DiscoveryOnlyRegistry(
                RegistryCapabilities.of(
                        RegistryCapability.REGISTRATION,
                        RegistryCapability.SUBSCRIPTION));

        assertThrows(
                IllegalStateException.class,
                invalid::registrar);
    }

    @Test
    void discoveryOnlyRegistryShouldNotExposeRegistrar() {
        Registry registry = new DiscoveryOnlyRegistry(
                RegistryCapabilities.of(
                        RegistryCapability.SUBSCRIPTION,
                        RegistryCapability.REVISION));

        assertTrue(registry.registrar().isEmpty());
    }

    private static final class DiscoveryOnlyRegistry implements Registry {
        private final RegistryCapabilities capabilities;

        private DiscoveryOnlyRegistry(
                RegistryCapabilities capabilities) {
            this.capabilities = capabilities;
        }

        @Override
        public RegistryCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public CompletionStage<RegistrySnapshot> lookup(ServiceKey key) {
            return CompletableFuture.completedFuture(
                    new RegistrySnapshot(List.of(), 1));
        }

        @Override
        public RegistrySubscription subscribe(
                ServiceKey key,
                RegistryListener listener) {
            listener.onSnapshot(new RegistrySnapshot(List.of(), 1));
            return () -> {
            };
        }

        @Override
        public void close() {
        }
    }
}
