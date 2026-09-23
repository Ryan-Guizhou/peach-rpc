package io.peach.rpc.spi;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 基于 Java ServiceLoader 的具名扩展加载器。
 *
 * <p>扩展描述在加载扩展点时完成发现，具体扩展实例延迟到首次使用时创建并缓存。
 * 同一扩展点内不允许出现重复扩展名，以便在启动阶段尽早暴露配置错误。
 *
 * @param <T> 扩展点类型
 */
public final class ExtensionLoader<T> {

    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>();

    private final Class<T> type;
    private final String defaultName;
    private final Map<String, Supplier<T>> factories;
    private final Map<String, T> instances = new ConcurrentHashMap<>();

    private ExtensionLoader(Class<T> type) {
        this.type = type;
        SPI spi = type.getAnnotation(SPI.class);
        if (spi == null) {
            throw new IllegalArgumentException(
                    "Extension point must be annotated with @SPI: " + type.getName());
        }
        this.defaultName = spi.value();
        this.factories = discover(type);
    }

    /**
     * 获取指定扩展点的加载器。
     *
     * @param type 扩展点类型
     * @param <T> 扩展点类型
     * @return 复用的扩展加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getLoader(Class<T> type) {
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, ExtensionLoader::new);
    }

    /**
     * 获取扩展点声明的默认实现。
     *
     * @return 默认扩展实例
     */
    public T getDefaultExtension() {
        if (defaultName == null || defaultName.isBlank()) {
            throw new IllegalStateException(
                    "No default extension configured for " + type.getName());
        }
        return getExtension(defaultName);
    }

    /**
     * 按名称获取扩展实例。
     *
     * @param name 扩展名称
     * @return 扩展实例
     */
    public T getExtension(String name) {
        Supplier<T> factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown extension '" + name + "' for " + type.getName()
                            + ". Available: " + factories.keySet());
        }
        return instances.computeIfAbsent(name, ignored -> factory.get());
    }

    /**
     * 返回当前可用的扩展名称。
     *
     * @return 不可变扩展名称集合
     */
    public Set<String> names() {
        return factories.keySet();
    }

    private static <T> Map<String, Supplier<T>> discover(Class<T> type) {
        Map<String, Supplier<T>> found = new LinkedHashMap<>();
        ServiceLoader.load(type).stream().forEach(provider -> {
            Class<? extends T> implementation = provider.type();
            Extension extension = implementation.getAnnotation(Extension.class);
            if (extension == null || extension.value().isBlank()) {
                throw new IllegalStateException(
                        "Extension implementation must declare @Extension: "
                                + implementation.getName());
            }
            Supplier<T> previous = found.putIfAbsent(
                    extension.value(), constructorSupplier(implementation));
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate extension name '" + extension.value()
                                + "' for " + type.getName());
            }
        });
        return Map.copyOf(found);
    }

    private static <T> Supplier<T> constructorSupplier(Class<? extends T> implementation) {
        return () -> {
            try {
                Constructor<? extends T> constructor = implementation.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(
                        "Failed to instantiate extension " + implementation.getName(), error);
            }
        };
    }
}
