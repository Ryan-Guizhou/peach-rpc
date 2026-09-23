package io.peach.rpc.spring.processor;

import io.peach.rpc.core.PeachRpcServer;
import io.peach.rpc.spring.annotation.PeachRpcService;
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

    private final ObjectProvider<PeachRpcServer> serverProvider;

    /**
     * 创建 Provider 服务导出处理器。
     *
     * @param serverProvider Provider 运行时延迟提供器
     */
    public PeachRpcServiceBeanPostProcessor(ObjectProvider<PeachRpcServer> serverProvider) {
        this.serverProvider = serverProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        PeachRpcService annotation = AnnotatedElementUtils.findMergedAnnotation(
                targetClass, PeachRpcService.class);
        if (annotation == null) {
            return bean;
        }

        Class<?> serviceInterface = resolveServiceInterface(targetClass, annotation);
        PeachRpcServer server = serverProvider.getObject();
        server.registerService(
                serviceInterface,
                bean,
                annotation.version(),
                annotation.group());
        return bean;
    }

    private static Class<?> resolveServiceInterface(
            Class<?> targetClass, PeachRpcService annotation) {
        if (annotation.interfaceClass() != void.class) {
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
