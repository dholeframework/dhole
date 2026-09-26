package org.dhole.internal.build;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.dhole.internal.module.ModuleDescriptor;
import org.dhole.internal.module.ModuleIndex;

/**
 * Writes the application's {@code META-INF/dhole/modules.idx} (MODULE_SYSTEM.md §29) from the module
 * descriptors ({@code META-INF/dhole/module.idx}) inside the selected runtime artifacts. Each known
 * artifact is opened directly; nothing is scanned at runtime. The module graph is validated here so
 * a broken graph fails the build, not the application's startup.
 */
public final class ModulesIndexGenerator {

    private ModulesIndexGenerator() {
    }

    /**
     * Internal repository harness only (the Dhole build calls {@link #write} directly): writes the
     * index for the given runtime artifacts into a resource directory.
     *
     * <p>Arguments: the output directory, then the runtime artifacts.
     */
    public static void main(String[] args) {
        List<Path> runtime = new ArrayList<>();
        for (int index = 1; index < args.length; index++) {
            runtime.add(Path.of(args[index]));
        }
        write(runtime, Path.of(args[0]));
    }

    /**
     * @throws BuildException if a descriptor is malformed or the module graph is invalid
     */
    public static String generate(List<Path> runtime) {
        List<ModuleDescriptor> modules = new ArrayList<>();
        for (Path artifact : runtime) {
            descriptor(artifact).ifPresent(modules::add);
        }
        String text = ModuleIndex.format(modules);
        try {
            ModuleIndex.parse(text).activationOrder();
        } catch (IllegalStateException e) {
            throw new BuildException(e.getMessage().replace("\n\nRebuild the application.", ""), e);
        }
        return text;
    }

    /**
     * Writes the index into a class output directory.
     */
    public static void write(List<Path> runtime, Path classes) {
        Path file = classes.resolve(ModuleIndex.LOCATION);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, generate(runtime), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to write " + ModuleIndex.LOCATION + ": " + e.getMessage(), e);
        }
    }

    private static Optional<ModuleDescriptor> descriptor(Path artifact) {
        if (Files.isDirectory(artifact)) {
            Path descriptor = artifact.resolve(ModuleIndex.DESCRIPTOR_LOCATION);
            try {
                return Files.exists(descriptor)
                        ? Optional.of(ModuleIndex.parseDescriptor(Files.readString(descriptor, StandardCharsets.UTF_8)))
                        : Optional.empty();
            } catch (IOException e) {
                throw new BuildException("Build Error\n\nUnable to read " + descriptor + ": " + e.getMessage(), e);
            }
        }
        try (ZipFile jar = new ZipFile(artifact.toFile())) {
            ZipEntry entry = jar.getEntry(ModuleIndex.DESCRIPTOR_LOCATION);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return Optional.of(ModuleIndex.parseDescriptor(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new BuildException("Build Error\n\nUnable to read " + artifact.getFileName() + ": " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new BuildException("Installation Error\n\nThe module descriptor of " + artifact.getFileName()
                    + " is invalid:\n" + e.getMessage() + "\n\nReinstall Dhole.", e);
        }
    }
}
