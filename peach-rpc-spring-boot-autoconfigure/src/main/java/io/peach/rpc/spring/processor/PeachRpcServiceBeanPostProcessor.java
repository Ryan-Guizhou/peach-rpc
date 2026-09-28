package io.peach.rpc.spring.processor;

import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.spring.annotation.PeachRpcService;
import io.peach.rpc.spring.runtime.PeachRpcRuntimeCoordinator;
import java.util.Arrays;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 将 {@link PeachRpcService} Spring Bean 注册到 Peach RPC Provider。
 */
public final class PeachRpcServiceBeanPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<PeachRpcRuntimeCoordinator> coordinatorProvider;

    /**
     * 创建 Provider 服务导出处理器。
     *
     * @param coordinatorProvider 运行时协调器延迟提供器
     */
    public PeachRpcServiceBeanPostProcessor(
            ObjectProvider<PeachRpcRuntimeCoordinator> coordinatorProvider) {
        this.coordinatorProvider = coordinatorProvider;
    }

    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        PeachRpcService annotation = AnnotatedElementUtils.findMergedAnnotation(
                targetClass,
                PeachRpcService.class);
        if (annotation == null) {
            return bean;
        }

        Class<?> serviceInterface = resolveServiceInterface(targetClass, annotation);
        PeachRpcServer server;
        try {
            server = coordinatorProvider.getObject().server();
        } catch (IllegalStateException error) {
            throw new IllegalStateException(
                    "Failed to initialize @PeachRpcService: bean="
                            + beanName
                            + ", interface="
                            + serviceInterface.getName(),
                    error);
        }
        server.registerService(
                serviceInterface,
                bean,
                annotation.version(),
                annotation.group());
        return bean;
    }

    private static Class<?> resolveServiceInterface(
            Class<?> targetClass,
            PeachRpcService annotation) {
        if (annotation.interfaceClass() != void.class) {
            if (!annotation.interfaceClass().isAssignableFrom(targetClass)) {
                throw new IllegalStateException(
                        "@PeachRpcService implementation does not implement interface: implementation="
                                + targetClass.getName()
                                + ", interface="
                                + annotation.interfaceClass().getName());
            }
            return annotation.interfaceClass();
        }
        Class<?>[] interfaces = targetClass.getInterfaces();
        if (interfaces.length != 1) {
            throw new IllegalStateException(
                    "Unable to infer RPC service interface for "
                            + targetClass.getName()
                            + ". Configure interfaceClass explicitly. interfaces="
                            + Arrays.toString(interfaces));
        }
        return interfaces[0];
    }
}
