/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.forgespi.language;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import ports.projecte.lib.fml.loading.moddiscovery.ModAnnotation;

/**
 * The annotations found in a mod's classes. Forge collects these while discovering mods; the port reads the class
 * files of its own modules with ASM the first time they are needed.
 */
public final class ModFileScanData {

    public record AnnotationData(Type annotationType, ElementType targetType, Type clazz, String memberName, Map<String, Object> annotationData) {
    }

    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final List<IModFileInfo> modFileInfo;

    private ModFileScanData(IModFileInfo info) {
        this.modFileInfo = List.of(info);
    }

    public Set<AnnotationData> getAnnotations() {
        return annotations;
    }

    public List<IModFileInfo> getIModInfoData() {
        return modFileInfo;
    }

    public static ModFileScanData scan(IModFileInfo info, List<Path> roots) {
        ModFileScanData data = new ModFileScanData(info);
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".class")).forEach(path -> {
                    try (InputStream stream = Files.newInputStream(path)) {
                        new ClassReader(stream).accept(new Scanner(data.annotations::add), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return data;
    }

    private static final class Scanner extends ClassVisitor {

        private final Consumer<AnnotationData> sink;
        private Type clazz;

        Scanner(Consumer<AnnotationData> sink) {
            super(Opcodes.ASM9);
            this.sink = sink;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            clazz = Type.getObjectType(name);
        }

        private AnnotationVisitor collect(String descriptor, ElementType targetType, String memberName) {
            Map<String, Object> values = new HashMap<>();
            sink.accept(new AnnotationData(Type.getType(descriptor), targetType, clazz, memberName, values));
            return new ValueCollector(values::put);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return collect(descriptor, ElementType.TYPE, clazz.getClassName());
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    return collect(annotationDescriptor, ElementType.FIELD, name);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    return collect(annotationDescriptor, ElementType.METHOD, name + descriptor);
                }
            };
        }
    }

    private interface ValueSink {

        void accept(String name, Object value);
    }

    /**
     * Stores values the way Forge's scanner does: classes as {@link Type}, enums as {@link ModAnnotation.EnumHolder},
     * arrays as lists and nested annotations as maps.
     */
    private static final class ValueCollector extends AnnotationVisitor {

        private final ValueSink sink;

        ValueCollector(ValueSink sink) {
            super(Opcodes.ASM9);
            this.sink = sink;
        }

        @Override
        public void visit(String name, Object value) {
            sink.accept(name, value);
        }

        @Override
        public void visitEnum(String name, String descriptor, String value) {
            sink.accept(name, new ModAnnotation.EnumHolder(descriptor, value));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String descriptor) {
            Map<String, Object> nested = new HashMap<>();
            sink.accept(name, nested);
            return new ValueCollector(nested::put);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            List<Object> values = new ArrayList<>();
            sink.accept(name, values);
            return new ValueCollector((ignored, value) -> values.add(value));
        }
    }
}
