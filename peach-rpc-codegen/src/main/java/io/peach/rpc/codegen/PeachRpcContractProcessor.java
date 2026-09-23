package io.peach.rpc.codegen;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * 为 {@code @PeachRpcContract} 服务接口生成无动态代理的 Consumer Stub。
 */
@SupportedAnnotationTypes("io.peach.rpc.api.PeachRpcContract")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class PeachRpcContractProcessor extends AbstractProcessor {

    /** 创建 Peach RPC 契约处理器。 */
    public PeachRpcContractProcessor() {
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnvironment) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(annotation)) {
                if (element instanceof TypeElement service) {
                    processService(service);
                }
            }
        }
        return true;
    }

    private void processService(TypeElement service) {
        if (service.getKind() != ElementKind.INTERFACE) {
            error(service, "@PeachRpcContract can only target interfaces");
            return;
        }
        if (!service.getTypeParameters().isEmpty()) {
            error(service, "Generic RPC service interfaces are not supported yet");
            return;
        }
        if (service.getNestingKind().isNested()) {
            error(service, "Nested RPC service interfaces are not supported yet");
            return;
        }

        List<ExecutableElement> methods = collectMethods(service);
        if (methods == null) {
            return;
        }

        String packageName = processingEnv.getElementUtils()
                .getPackageOf(service)
                .getQualifiedName()
                .toString();
        String serviceName = service.getSimpleName().toString();
        String factoryName = serviceName + "PeachRpcClientFactory";
        String qualifiedFactory = packageName.isEmpty()
                ? factoryName
                : packageName + '.' + factoryName;

        try {
            JavaFileObject source = processingEnv.getFiler()
                    .createSourceFile(qualifiedFactory, service);
            try (Writer writer = source.openWriter()) {
                writer.write(generate(
                        packageName,
                        service,
                        factoryName,
                        methods));
            }
        } catch (FilerException ignored) {
            // 同一轮增量编译可能已经生成目标文件。
        } catch (IOException error) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to generate Peach RPC client stub: " + error.getMessage(),
                    service);
        }
    }

    private List<ExecutableElement> collectMethods(TypeElement service) {
        Map<String, ExecutableElement> unique = new LinkedHashMap<>();
        Map<Integer, ExecutableElement> ids = new HashMap<>();
        for (ExecutableElement method : ElementFilter.methodsIn(
                processingEnv.getElementUtils().getAllMembers(service))) {
            if (!method.getModifiers().contains(Modifier.ABSTRACT)
                    || method.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (!method.getTypeParameters().isEmpty()) {
                error(method, "Generic RPC methods are not supported yet");
                return null;
            }
            String signature = sourceSignature(method);
            unique.putIfAbsent(signature, method);
        }

        List<ExecutableElement> methods = new ArrayList<>(unique.values());
        methods.sort(Comparator.comparing(this::sourceSignature));
        for (ExecutableElement method : methods) {
            int methodId = methodId(method);
            ExecutableElement collision = ids.putIfAbsent(methodId, method);
            if (collision != null) {
                error(
                        method,
                        "RPC method id collision with " + collision.getSimpleName());
                return null;
            }
        }
        return methods;
    }

    private String generate(
            String packageName,
            TypeElement service,
            String factoryName,
            List<ExecutableElement> methods) {
        String serviceType = service.getQualifiedName().toString();
        String clientName = "GeneratedClient";
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("/** ").append(service.getSimpleName())
                .append(" 的 Peach RPC 编译期 Consumer Stub 工厂。 */\n")
                .append("@").append(Generated.class.getName())
                .append("(\"").append(getClass().getName()).append("\")\n")
                .append("public final class ").append(factoryName)
                .append(" implements io.peach.rpc.generated.RpcGeneratedClientFactory<")
                .append(serviceType).append("> {\n\n")
                .append("    /** 创建生成式 Consumer Stub 工厂。 */\n")
                .append("    public ").append(factoryName).append("() {\n")
                .append("    }\n\n");

        for (int index = 0; index < methods.size(); index++) {
            source.append("    private static final int METHOD_")
                    .append(index)
                    .append(" = ")
                    .append(methodId(methods.get(index)))
                    .append(";\n");
        }
        if (!methods.isEmpty()) {
            source.append('\n');
        }

        source.append("    @Override\n")
                .append("    public Class<").append(serviceType)
                .append("> serviceType() {\n")
                .append("        return ").append(serviceType).append(".class;\n")
                .append("    }\n\n")
                .append("    @Override\n")
                .append("    public ").append(serviceType)
                .append(" create(io.peach.rpc.generated.RpcGeneratedInvocation invocation) {\n")
                .append("        return new ").append(clientName)
                .append("(invocation);\n")
                .append("    }\n\n")
                .append("    /** 无动态代理的编译期 Consumer Stub。 */\n")
                .append("    @SuppressWarnings(\"unchecked\")\n")
                .append("    private static final class ").append(clientName)
                .append(" implements ").append(serviceType).append(" {\n")
                .append("        private final io.peach.rpc.generated.RpcGeneratedInvocation invocation;\n\n")
                .append("        private ").append(clientName)
                .append("(io.peach.rpc.generated.RpcGeneratedInvocation invocation) {\n")
                .append("            this.invocation = java.util.Objects.requireNonNull(invocation, \"invocation\");\n")
                .append("        }\n\n");

        for (int index = 0; index < methods.size(); index++) {
            appendMethod(source, methods.get(index), index);
        }

        source.append("    }\n")
                .append("}\n");
        return source.toString();
    }

    private void appendMethod(
            StringBuilder source,
            ExecutableElement method,
            int index) {
        source.append("        /**\n")
                .append("         * 编译期生成的 RPC 方法实现。\n");
        for (VariableElement parameter : method.getParameters()) {
            source.append("         * @param ")
                    .append(parameter.getSimpleName())
                    .append(" RPC 参数\n");
        }
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            source.append("         * @return RPC 调用结果\n");
        }
        source.append("         */\n")
                .append("        @Override\n")
                .append("        public ")
                .append(method.getReturnType())
                .append(' ')
                .append(method.getSimpleName())
                .append('(');

        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            VariableElement parameter = method.getParameters().get(i);
            source.append(parameter.asType())
                    .append(' ')
                    .append(parameter.getSimpleName());
        }
        source.append(") {\n")
                .append("            Object[] arguments = new Object[] {");
        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            source.append(method.getParameters().get(i).getSimpleName());
        }
        source.append("};\n");

        TypeMirror returnType = method.getReturnType();
        String erasedReturn = processingEnv.getTypeUtils()
                .erasure(returnType)
                .toString();
        if (returnType.getKind() == TypeKind.VOID) {
            source.append("            invocation.invoke(METHOD_")
                    .append(index)
                    .append(", arguments).toCompletableFuture().join();\n")
                    .append("        }\n\n");
            return;
        }
        if ("java.util.concurrent.CompletionStage".equals(erasedReturn)) {
            source.append("            return (")
                    .append(returnType)
                    .append(") (java.util.concurrent.CompletionStage<?>) invocation.invoke(METHOD_")
                    .append(index)
                    .append(", arguments);\n")
                    .append("        }\n\n");
            return;
        }
        if ("java.util.concurrent.CompletableFuture".equals(erasedReturn)) {
            source.append("            return (")
                    .append(returnType)
                    .append(") (java.util.concurrent.CompletableFuture<?>) invocation.invoke(METHOD_")
                    .append(index)
                    .append(", arguments).toCompletableFuture();\n")
                    .append("        }\n\n");
            return;
        }

        source.append("            Object result = invocation.invoke(METHOD_")
                .append(index)
                .append(", arguments).toCompletableFuture().join();\n")
                .append("            return ")
                .append(castExpression(returnType, "result"))
                .append(";\n")
                .append("        }\n\n");
    }

    private String castExpression(TypeMirror type, String value) {
        return switch (type.getKind()) {
            case BOOLEAN -> "(java.lang.Boolean) " + value;
            case BYTE -> "(java.lang.Byte) " + value;
            case SHORT -> "(java.lang.Short) " + value;
            case INT -> "(java.lang.Integer) " + value;
            case LONG -> "(java.lang.Long) " + value;
            case CHAR -> "(java.lang.Character) " + value;
            case FLOAT -> "(java.lang.Float) " + value;
            case DOUBLE -> "(java.lang.Double) " + value;
            default -> "(" + type + ") " + value;
        };
    }

    private String sourceSignature(ExecutableElement method) {
        StringBuilder value = new StringBuilder(method.getSimpleName()).append('(');
        for (VariableElement parameter : method.getParameters()) {
            value.append(processingEnv.getTypeUtils().erasure(parameter.asType()))
                    .append(';');
        }
        return value.append(')').toString();
    }

    private int methodId(ExecutableElement method) {
        StringBuilder value = new StringBuilder(method.getSimpleName()).append('(');
        for (VariableElement parameter : method.getParameters()) {
            value.append(runtimeClassName(parameter.asType())).append(';');
        }
        value.append(')').append(runtimeClassName(method.getReturnType()));
        return fnv1a32(value.toString());
    }

    private String runtimeClassName(TypeMirror original) {
        TypeMirror type = processingEnv.getTypeUtils().erasure(original);
        return switch (type.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID ->
                    type.toString();
            case ARRAY -> arrayDescriptor((ArrayType) type);
            case DECLARED -> processingEnv.getElementUtils()
                    .getBinaryName((TypeElement) ((DeclaredType) type).asElement())
                    .toString();
            default -> throw new IllegalArgumentException(
                    "Unsupported RPC signature type: " + type);
        };
    }

    private String arrayDescriptor(ArrayType array) {
        TypeMirror component = processingEnv.getTypeUtils()
                .erasure(array.getComponentType());
        return "[" + switch (component.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case CHAR -> "C";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case ARRAY -> arrayDescriptor((ArrayType) component);
            case DECLARED -> "L"
                    + processingEnv.getElementUtils()
                            .getBinaryName((TypeElement) ((DeclaredType) component).asElement())
                    + ";";
            default -> throw new IllegalArgumentException(
                    "Unsupported RPC array component: " + component);
        };
    }

    private static int fnv1a32(String value) {
        int hash = 0x811c9dc5;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xff;
            hash *= 0x01000193;
        }
        return hash;
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                message,
                element);
    }
}
