package io.peach.rpc.spring.processor;

import io.peach.rpc.spring.annotation.PeachRpcService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 在 Provider 实例化前校验 Bean 生命周期，避免 Server 启动后才出现服务。
 */
public final class PeachRpcServiceBeanDefinitionValidator
        implements BeanFactoryPostProcessor, PriorityOrdered {

    /** 创建 Provider Bean 定义校验器。 */
    public PeachRpcServiceBeanDefinitionValidator() {
    }

    @Override
    public void postProcessBeanFactory(
            ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(beanName, false);
            if (type == null
                    || AnnotatedElementUtils.findMergedAnnotation(
                            type,
                            PeachRpcService.class) == null) {
                continue;
            }
            BeanDefinition definition =
                    beanFactory.getBeanDefinition(beanName);
            if (definition.isLazyInit()) {
                throw new IllegalStateException(
                        "@PeachRpcService must not be lazy: bean="
                                + beanName
                                + ", type="
                                + type.getName());
            }
            if (!beanFactory.isSingleton(beanName)) {
                throw new IllegalStateException(
                        "@PeachRpcService must be singleton: bean="
                                + beanName
                                + ", type="
                                + type.getName());
            }
        }
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 50;
    }
}
