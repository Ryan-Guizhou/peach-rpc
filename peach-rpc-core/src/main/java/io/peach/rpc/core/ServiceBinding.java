package io.peach.rpc.core;

import io.peach.rpc.api.RpcIds;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Provider 启动阶段预解析的服务分派表。 */
final class ServiceBinding {
    private final Map<Integer, Invoker> methods;

    ServiceBinding(Class<?> api, Object target) {
        Map<Integer, Invoker> resolved = new HashMap<>();
        for (Method method : api.getMethods()) {
            try {
                int methodId = RpcIds.methodId(method);
                Method implementation = target.getClass()
                        .getMethod(method.getName(), method.getParameterTypes());
                MethodHandle handle = MethodHandles.publicLookup()
                        .unreflect(implementation)
                        .bindTo(target);
                Invoker previous = resolved.putIfAbsent(methodId, new Invoker(method, handle));
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

    Object invoke(int methodId, Object[] arguments) throws Throwable {
        Invoker invoker = methods.get(methodId);
        if (invoker == null) {
            throw new NoSuchMethodException("Unknown method id: " + methodId);
        }
        return invoker.handle().invokeWithArguments(arguments);
    }

    private record Invoker(Method method, MethodHandle handle) {}
}
