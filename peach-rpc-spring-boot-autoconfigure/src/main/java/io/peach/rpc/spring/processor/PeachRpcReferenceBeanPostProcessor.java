package io.peach.rpc.spring.processor;

import io.peach.rpc.core.PeachRpcClient;
import io.peach.rpc.spring.annotation.PeachRpcReference;
import java.lang.reflect.Field;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

/**
 * 将 {@link PeachRpcReference} 字段替换为 RPC Consumer 代理。
 */
public final class PeachRpcReferenceBeanPostProcessor
        implements InstantiationAwareBeanPostProcessor, PriorityOrdered {

    private final ObjectProvider<PeachRpcClient> clientProvider;

    /**
     * 创建 Consumer 引用注入处理器。
     *
     * @param clientProvider Consumer 运行时延迟提供器
     */
    public PeachRpcReferenceBeanPostProcessor(ObjectProvider<PeachRpcClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), field -> injectReference(bean, field));
        return bean;
    }

    private void injectReference(Object bean, Field field) {
        PeachRpcReference reference = field.getAnnotation(PeachRpcReference.class);
        if (reference == null) {
            return;
        }
        if (!field.getType().isInterface()) {
            throw new IllegalStateException(
                    "@PeachRpcReference requires an interface field: " + field);
        }
        PeachRpcClient client = clientProvider.getObject();
        Object proxy = client.refer(field.getType(), reference.version(), reference.group());
        ReflectionUtils.makeAccessible(field);
        ReflectionUtils.setField(field, bean, proxy);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
