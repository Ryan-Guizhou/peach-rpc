package io.peach.rpc.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.peach.rpc.api.PeachRpcExecution;
import io.peach.rpc.api.RpcExecutionMode;
import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodec;
import io.peach.rpc.codec.RpcCodecRegistry;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ServiceBindingExecutionTest {

    @Test
    void shouldResolveExecutionModePerMethod() throws Exception {
        ServiceKey key =
                new ServiceKey("example.ExecutionService", "1.0.0", "default");
        ServiceBinding binding = new ServiceBinding(
                key,
                ExecutionService.class,
                new ExecutionServiceImpl(),
                RpcCodecRegistry.of(new NoopCodec()));

        assertEquals(
                RpcExecutionMode.BLOCKING_VIRTUAL,
                binding.executionMode(methodId("blocking")));
        assertEquals(
                RpcExecutionMode.CPU,
                binding.executionMode(methodId("cpu")));
        assertEquals(
                RpcExecutionMode.DIRECT,
                binding.executionMode(methodId("direct")));
        assertTrue(binding.usesDirectExecution());
    }

    @Test
    void shouldReportNoDirectExecutionForSafeService() {
        ServiceKey key =
                new ServiceKey("example.SafeService", "1.0.0", "default");
        ServiceBinding binding = new ServiceBinding(
                key,
                SafeService.class,
                new SafeServiceImpl(),
                RpcCodecRegistry.of(new NoopCodec()));

        assertFalse(binding.usesDirectExecution());
    }

    private static int methodId(String name) throws Exception {
        Method method = ExecutionService.class.getMethod(name);
        return RpcIds.methodId(method);
    }

    interface ExecutionService {
        String blocking();

        @PeachRpcExecution(RpcExecutionMode.CPU)
        String cpu();

        @PeachRpcExecution(RpcExecutionMode.DIRECT)
        String direct();
    }

    static final class ExecutionServiceImpl implements ExecutionService {
        @Override
        public String blocking() {
            return "blocking";
        }

        @Override
        public String cpu() {
            return "cpu";
        }

        @Override
        public String direct() {
            return "direct";
        }
    }

    interface SafeService {
        String execute();
    }

    static final class SafeServiceImpl implements SafeService {
        @Override
        public String execute() {
            return "ok";
        }
    }

    static final class NoopCodec implements RpcCodec {
        @Override
        public byte code() {
            return 1;
        }

        @Override
        public byte[] encode(Object value) {
            return new byte[0];
        }

        @Override
        public <T> T decode(byte[] bytes, Class<T> type) {
            return null;
        }
    }
}
