package org.dhole.internal.compiler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles in-memory Java sources with the metadata processor into a directory. Public for the
 * end-to-end tests in other test packages.
 */
public final class TestCompiler {

    private final Map<String, String> sources = new TreeMap<>();
    private final List<String> options = new ArrayList<>();
    private final List<Path> classpath = new ArrayList<>();

    public static TestCompiler create() {
        return new TestCompiler();
    }

    /**
     * Adds a source, for example {@code source("com.acme.App", "package com.acme; ...")}.
     */
    public TestCompiler source(String className, String code) {
        sources.put(className, code);
        return this;
    }

    public TestCompiler application(String className) {
        options.add("-A" + MetadataProcessor.APPLICATION_OPTION + "=" + className);
        return this;
    }

    TestCompiler option(String option) {
        options.add(option);
        return this;
    }

    TestCompiler classpath(Path entry) {
        classpath.add(entry);
        return this;
    }

    public Result compile(Path output, Processor... extraProcessors) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> units = sources.entrySet().stream()
                .map(entry -> (JavaFileObject) new Source(entry.getKey(), entry.getValue()))
                .toList();
        List<String> arguments = new ArrayList<>(options);
        arguments.add("-d");
        arguments.add(output.toString());
        String path = System.getProperty("java.class.path");
        for (Path entry : classpath) {
            path = entry + java.io.File.pathSeparator + path;
        }
        arguments.add("-classpath");
        arguments.add(path);
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, arguments, null, units);
            List<Processor> processors = new ArrayList<>();
            processors.add(new MetadataProcessor());
            processors.addAll(List.of(extraProcessors));
            task.setProcessors(processors);
            boolean success = task.call();
            return new Result(success, diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR
                            || d.getKind() == javax.tools.Diagnostic.Kind.WARNING)
                    .map(d -> d.getKind() + ": " + d.getMessage(null)
                            + (d.getSource() == null ? "" : " @ " + Path.of(d.getSource().getName()).getFileName()
                                    + ":" + d.getLineNumber()))
                    .toList(), output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Result(boolean success, List<String> diagnostics, Path output) {

        public Optional<String> index() {
            Path file = output.resolve(ComponentIndexWriter.LOCATION);
            try {
                return Files.exists(file) ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String code;

        Source(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
