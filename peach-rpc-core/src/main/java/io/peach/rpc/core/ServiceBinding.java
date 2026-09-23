package io.peach.rpc.core;

import io.peach.rpc.api.RpcIds;
import io.peach.rpc.api.RpcMethodDescriptor;
import io.peach.rpc.api.ServiceKey;
import io.peach.rpc.codec.RpcCodecRegistry;
import io.peach.rpc.codec.RpcMethodCodec;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Provider 启动阶段预解析的服务分派表。 */
final class ServiceBinding {
    private final Map<Integer, Invoker> methods;

    ServiceBinding(
            ServiceKey serviceKey,
            Class<?> api,
            Object target,
            RpcCodecRegistry codecs) {
        Map<Integer, Invoker> resolved = new HashMap<>();
        for (Method method : api.getMethods()) {
            try {
                int methodId = RpcIds.methodId(method);
                Method implementation = target.getClass()
                        .getMethod(method.getName(), method.getParameterTypes());
                MethodHandle handle = MethodHandles.publicLookup()
                        .unreflect(implementation)
                        .bindTo(target);
                RpcMethodDescriptor descriptor = RpcMethodDescriptor.from(serviceKey, method);
                Map<Byte, RpcMethodCodec> methodCodecs = new HashMap<>();
                for (byte codecId : codecs.supportedCodecIds()) {
                    methodCodecs.put(codecId, codecs.bind(descriptor, codecId));
                }
                Invoker previous = resolved.putIfAbsent(
                        methodId,
                        new Invoker(handle, Map.copyOf(methodCodecs)));
                if (previous != null) {
                    throw new IllegalStateException(
                            "Method id collision in " + api.getName() + ": " + methodId);
                }
            } catch (ReflectiveOperationException error) {
                throw new IllegalArgumentException(
                        "Service implementation does not implement " + method, error);
            }
        }
        this.methods = Map.copyOf(resolved);
    }

    RpcMethodCodec codec(int methodId, byte codecId) throws NoSuchMethodException {
        Invoker invoker = require(methodId);
        RpcMethodCodec codec = invoker.codecs().get(codecId);
        if (codec == null) {
            throw new IllegalArgumentException(
                    "Unsupported codec " + Byte.toUnsignedInt(codecId)
                            + " for method " + methodId);
        }
        return codec;
    }

    Object invoke(int methodId, Object[] arguments) throws Throwable {
        return require(methodId).handle().invokeWithArguments(arguments);
    }

    private Invoker require(int methodId) throws NoSuchMethodException {
        Invoker invoker = methods.get(methodId);
        if (invoker == null) {
            throw new NoSuchMethodException("Unknown method id: " + methodId);
        }
        return invoker;
    }

    private record Invoker(
            MethodHandle handle,
            Map<Byte, RpcMethodCodec> codecs) {
    }
}
