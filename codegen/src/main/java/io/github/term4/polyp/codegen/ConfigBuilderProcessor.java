package io.github.term4.polyp.codegen;

import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Emits {@code <Config>BuilderBase} for each {@link GenerateBuilder} config; see the annotation for the contract. */
@SupportedAnnotationTypes({"io.github.term4.polyp.codegen.GenerateBuilder", "io.github.term4.polyp.codegen.CheckResolveOrder"})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class ConfigBuilderProcessor extends AbstractProcessor {

    private static final String FIELD_VALUE = "io.github.term4.polyp.config.FieldValue";

    private record Knob(String type, String name) {}

    /**
     * AST scan for a call that actually carries {@code param} across: a bare {@code super()}, or a copy call
     * naming something else, is the very regression these checks exist to catch.
     */
    private static final class CopyCallScanner extends com.sun.source.util.TreeScanner<Boolean, Void> {
        private final String param;
        private final boolean superCounts;

        CopyCallScanner(String param, boolean superCounts) {
            this.param = param;
            this.superCounts = superCounts;
        }

        @Override public Boolean visitMethodInvocation(com.sun.source.tree.MethodInvocationTree node, Void p) {
            String select = node.getMethodSelect().toString();
            boolean carrier = (superCounts && select.equals("super"))
                    || select.endsWith("copyKnobs") || select.endsWith("mergeKnobs")
                    || select.equals("super.fromBase");
            if (carrier && passes(node)) return true;
            return super.visitMethodInvocation(node, p);
        }

        // the parameter has to appear as an argument, not merely somewhere in the body
        private boolean passes(com.sun.source.tree.MethodInvocationTree node) {
            if (node.getArguments().isEmpty()) return false;
            for (var arg : node.getArguments()) {
                if (arg.toString().equals(param)) return true;
            }
            return false;
        }

        @Override public Boolean reduce(Boolean a, Boolean b) { return Boolean.TRUE.equals(a) || Boolean.TRUE.equals(b); }
    }

    private static String soleParam(ExecutableElement method) {
        return method.getParameters().isEmpty() ? "" : method.getParameters().getFirst().getSimpleName().toString();
    }

    private Trees trees; // javac-only AST access for the copy-ctor check; null under other compilers

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        try {
            trees = Trees.instance(env);
        } catch (RuntimeException e) {
            trees = null;
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element e : round.getElementsAnnotatedWith(GenerateBuilder.class)) {
            if (e instanceof TypeElement config) {
                generate(config);
                checkCopyConstructors(config);
                checkFromBase(config);
                checkPathEditShape(config);
            }
        }
        for (Element e : round.getElementsAnnotatedWith(CheckResolveOrder.class)) {
            if (e instanceof TypeElement resolver) checkResolveOrder(resolver);
        }
        return false;
    }

    /** A {@code fromBase} that forgets {@code mergeKnobs} silently drops every generated knob of the overlay. */
    private void checkFromBase(TypeElement config) {
        if (trees == null) return;
        for (Element member : config.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD || !member.getSimpleName().contentEquals("fromBase")) continue;
            ExecutableElement method = (ExecutableElement) member;
            var tree = trees.getTree(method);
            String base = soleParam(method);
            if (tree != null && tree.getBody() != null && !base.isEmpty()
                    && !Boolean.TRUE.equals(new CopyCallScanner(base, false).scan(tree.getBody(), null))) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "fromBase must merge the generated knobs of " + base + " (mergeKnobs, or super.fromBase)", member);
            }
        }
    }

    /** {@link CheckResolveOrder}: every {@code cfg.<knob>} argument must land on the same-named record component. */
    private void checkResolveOrder(TypeElement resolver) {
        if (trees == null) return;
        var path = trees.getPath(resolver);
        if (path == null) return;
        new com.sun.source.util.TreePathScanner<Void, Void>() {
            @Override public Void visitNewClass(com.sun.source.tree.NewClassTree node, Void p) {
                Element ctor = trees.getElement(new com.sun.source.util.TreePath(getCurrentPath(), node));
                if (ctor instanceof ExecutableElement ex
                        && ex.getEnclosingElement() instanceof TypeElement type
                        && type.getKind() == ElementKind.RECORD) {
                    var components = type.getRecordComponents();
                    var args = node.getArguments();
                    if (components.size() == args.size()) {
                        for (int i = 0; i < args.size(); i++) {
                            String read = firstComponentRead(args.get(i), type);
                            if (read != null && !components.get(i).getSimpleName().contentEquals(read)) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                        "argument " + (i + 1) + " of new " + type.getSimpleName() + " reads ." + read
                                                + " but the component there is " + components.get(i).getSimpleName(),
                                        resolver);
                            }
                        }
                    }
                }
                return super.visitNewClass(node, p);
            }

            /** The first {@code x.<name>} select in {@code arg} whose name matches a component of {@code type}. */
            private String firstComponentRead(com.sun.source.tree.Tree arg, TypeElement type) {
                var found = new String[1];
                new com.sun.source.util.TreeScanner<Void, Void>() {
                    @Override public Void visitMemberSelect(com.sun.source.tree.MemberSelectTree sel, Void p) {
                        if (found[0] == null) {
                            String name = sel.getIdentifier().toString();
                            for (var c : type.getRecordComponents()) {
                                if (c.getSimpleName().contentEquals(name)) { found[0] = name; break; }
                            }
                        }
                        return super.visitMemberSelect(sel, p);
                    }
                }.scan(arg, null);
                return found[0];
            }
        }.scan(path, null);
    }

    /**
     * A hand-written {@code Builder(Config c)} that forgets {@code super(c)} silently resets every generated
     * knob to defaults (this shipped: fireballFight() lost knockbackMultiplier through toBuilder()).
     */
    private void checkCopyConstructors(TypeElement config) {
        if (trees == null) return;
        for (Element member : config.getEnclosedElements()) {
            if (member.getKind() != ElementKind.CLASS || !member.getSimpleName().contentEquals("Builder")) continue;
            for (Element ctor : member.getEnclosedElements()) {
                if (ctor.getKind() != ElementKind.CONSTRUCTOR) continue;
                ExecutableElement ex = (ExecutableElement) ctor;
                boolean takesConfig = ex.getParameters().stream()
                        .anyMatch(p -> processingEnv.getTypeUtils().isSameType(p.asType(), config.asType()));
                if (!takesConfig) continue;
                var tree = trees.getTree(ex);
                String arg = ex.getParameters().stream()
                        .filter(p -> processingEnv.getTypeUtils().isSameType(p.asType(), config.asType()))
                        .findFirst().orElseThrow().getSimpleName().toString();
                if (tree != null && tree.getBody() != null
                        && !Boolean.TRUE.equals(new CopyCallScanner(arg, true).scan(tree.getBody(), null))) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Builder copy-constructor must call super(" + arg + ") - the generated knobs reset to defaults otherwise", ctor);
                }
            }
        }
    }

    /**
     * {@code PathEdits} reaches a config's builder by name - {@code builder()}, {@code build()},
     * {@code toBuilder()} - because it works over any generated config, not one known type. The names are this
     * processor's own, so the only way they can drift is a config that does not carry them: caught here, at the
     * config, rather than at a {@code /rules} edit months later.
     */
    private void checkPathEditShape(TypeElement config) {
        String cfg = config.getSimpleName().toString();
        boolean builder = false, toBuilder = false;
        for (Element member : config.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) member;
            String name = method.getSimpleName().toString();
            boolean isStatic = method.getModifiers().contains(javax.lang.model.element.Modifier.STATIC);
            if (name.equals("builder") && isStatic) builder = true;
            if (name.equals("toBuilder") && !isStatic && method.getParameters().isEmpty()) toBuilder = true;
        }
        if (!builder) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    cfg + " needs a static builder() - PathEdits builds every scoped edit through it", config);
        }
        if (!toBuilder) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    cfg + " needs a no-argument toBuilder() - an edit over an existing config copies through it", config);
        }
        for (Element member : config.getEnclosedElements()) {
            if (member.getKind() != ElementKind.CLASS || !member.getSimpleName().contentEquals("Builder")) continue;
            boolean build = false;
            for (Element inner : member.getEnclosedElements()) {
                if (inner.getKind() == ElementKind.METHOD && inner.getSimpleName().contentEquals("build")
                        && ((ExecutableElement) inner).getParameters().isEmpty()) build = true;
            }
            if (!build) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        cfg + ".Builder needs a no-argument build() - PathEdits closes every edit with it", member);
            }
            return;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                cfg + " needs a nested Builder class", config);
    }

    private void generate(TypeElement config) {
        String pkg = processingEnv.getElementUtils().getPackageOf(config).getQualifiedName().toString();
        String cfg = config.getSimpleName().toString();
        String base = cfg + "BuilderBase";

        String ctx = null;
        List<Knob> knobs = new ArrayList<>();
        for (Element member : config.getEnclosedElements()) {
            if (member.getKind() != ElementKind.FIELD) continue;
            if (!(member.asType() instanceof DeclaredType dt)) continue;
            if (!((TypeElement) dt.asElement()).getQualifiedName().contentEquals(FIELD_VALUE)) continue;
            List<? extends TypeMirror> args = dt.getTypeArguments();
            if (args.size() != 2) continue;
            ctx = args.get(0).toString();
            knobs.add(new Knob(args.get(1).toString(), member.getSimpleName().toString()));
        }
        if (ctx == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "no FieldValue fields to generate from", config);
            return;
        }

        StringBuilder s = new StringBuilder();
        s.append("package ").append(pkg).append(";\n\n")
         .append("import io.github.term4.polyp.config.FieldValue;\n\n")
         .append("import java.util.function.Function;\n\n")
        .append("/** Generated from {@link ").append(cfg).append("}'s FieldValue fields (")
        .append(GenerateBuilder.class.getSimpleName()).append(") - do not edit.")
        .append(" A bare {@code null} or an int literal for a Double knob is ambiguous between the constant and")
        .append(" function setters: write {@code (T) null} / {@code 2.0}. */\n")
         .append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
         .append("public abstract class ").append(base).append("<B extends ").append(base).append("<B>> {\n\n");
        for (Knob k : knobs) {
            s.append("    FieldValue<").append(ctx).append(", ").append(k.type).append("> ").append(k.name).append(";\n");
        }
        s.append("\n    protected abstract B self();\n\n")
         .append("    protected ").append(base).append("() {}\n\n")
         .append("    /** The copy route for hand-written {@code Builder(Config c)} ctors - enforced by the processor. */\n")
         .append("    protected ").append(base).append("(").append(cfg).append(" c) { copyKnobs(c); }\n\n");
        for (Knob k : knobs) {
            String t = k.type, n = k.name;
            s.append("    public B ").append(n).append("(").append(t).append(" v) { ").append(n).append(" = FieldValue.constant(v); return self(); }\n")
             .append("    public B ").append(n).append("(Function<").append(ctx).append(", ").append(t).append("> fn) { ").append(n).append(" = FieldValue.of(fn); return self(); }\n")
             .append("    public B ").append(n).append("(").append(t).append(" fallback, Function<").append(ctx).append(", ").append(t).append("> fn) { ").append(n).append(" = FieldValue.ofWithFallback(fallback, fn); return self(); }\n")
             .append("    B ").append(n).append("(FieldValue<").append(ctx).append(", ").append(t).append("> v) { ").append(n).append(" = v; return self(); }\n");
        }
        s.append("\n    /** Copies every generated knob from {@code c}. */\n    final void copyKnobs(").append(cfg).append(" c) {\n");
        for (Knob k : knobs) s.append("        ").append(k.name).append(" = c.").append(k.name).append(";\n");
        s.append("    }\n");
        s.append("\n    /** Sets every generated knob to {@code a} layered over {@code base} ({@code FieldValue.merge}). */\n    final void mergeKnobs(").append(cfg).append(" a, ").append(cfg).append(" base) {\n");
        for (Knob k : knobs) s.append("        ").append(k.name).append(" = FieldValue.merge(a.").append(k.name).append(", base.").append(k.name).append(");\n");
        s.append("    }\n");

        // the path-addressable knob table (config field <-> builder setter), consumed by PathEdits
        s.append("\n    /** Name -> knob, for {@link io.github.term4.polyp.config.PathEdits}. */\n")
         .append("    public static final java.util.Map<String, io.github.term4.polyp.config.ConfigKnob> KNOBS;\n")
         .append("    static {\n")
         .append("        java.util.Map<String, io.github.term4.polyp.config.ConfigKnob> m = new java.util.LinkedHashMap<>();\n");
        for (Knob k : knobs) {
            // generic value types have no class literal - registered as code-only (null valueType)
            String literal = k.type.contains("<") ? "null" : k.type + ".class";
            s.append("        m.put(\"").append(k.name).append("\", new io.github.term4.polyp.config.ConfigKnob(\"")
             .append(k.name).append("\", ").append(literal).append(", ").append(ctx).append(".class")
             .append(", c -> ((").append(cfg).append(") c).").append(k.name)
             .append(", (b, v) -> ((").append(base).append(") b).").append(k.name).append("((FieldValue) v)));\n");
        }
        s.append("        KNOBS = java.util.Map.copyOf(m);\n    }\n");

        // a scope that must DROP a preset value rather than layer another over it: the typed setters can
        // only set, and a cast null at the call site reads like a mistake
        s.append("\n    /** Unsets one generated knob by its {@link #KNOBS} name - what a scope uses to drop\n")
         .append("     *  a preset's value instead of layering another over it. An unknown name throws. */\n")
         .append("    public B clear(String knob) {\n")
         .append("        io.github.term4.polyp.config.ConfigKnob k = KNOBS.get(knob);\n")
         .append("        if (k == null) throw new IllegalArgumentException(\"no knob '\" + knob + \"' on ")
         .append(cfg).append(", known: \" + KNOBS.keySet());\n")
         .append("        k.set().accept(this, null);\n")
         .append("        return self();\n    }\n}\n");

        try {
            Writer w = processingEnv.getFiler().createSourceFile(pkg + "." + base, config).openWriter();
            try (w) { w.write(s.toString()); }
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "codegen failed: " + ex, config);
        }
    }
}
