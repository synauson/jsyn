package com.synauson.jsyn.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Extracts the cdylib and ORT native libraries from classpath JARs, loads them in
 * the correct order (ORT first, then the JNI cdylib), and caches the ORT absolute path
 * for passing to {@code initRuntime}.
 *
 * <p>All methods are thread-safe. {@link #load()} is idempotent: subsequent calls from
 * any thread return immediately once the first call completes.
 *
 * @since 0.1.0
 */
public final class NativeLoader {
    private static final String RESOURCE_PREFIX = "com/benashby/jsyn/natives/";
    private static volatile String ortDylibAbsolutePath;
    private static volatile boolean loaded;

    private NativeLoader() {}

    /**
     * Extract and load the ORT and JNI native libraries from the classpath. Idempotent.
     *
     * @throws UnsatisfiedLinkError if the native libraries are missing from the classpath
     *         or cannot be extracted to a temp directory
     */
    public static synchronized void load() {
        if (loaded) return;
        String platform = detectPlatform();
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("jsyn-natives-");
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("failed to create temp dir: " + e);
        }
        registerCleanupOnExit(tempDir);

        Path ortLib = extractResource(
            RESOURCE_PREFIX + platform + "/" + ortLibFileName(platform), tempDir);
        Path jniLib = extractResource(
            RESOURCE_PREFIX + platform + "/" + jniLibFileName(platform), tempDir);

        // Pre-load ORT so its symbols are available when the JNI cdylib is loaded.
        System.load(ortLib.toAbsolutePath().toString());
        // Load JNI cdylib — JNI_OnLoad runs and caches the JavaVM.
        System.load(jniLib.toAbsolutePath().toString());

        ortDylibAbsolutePath = ortLib.toAbsolutePath().toString();
        loaded = true;
    }

    /**
     * Returns the absolute path of the extracted ORT dynamic library. Must be called
     * after {@link #load()}.
     *
     * @return absolute filesystem path to the extracted ORT shared object/DLL
     * @throws IllegalStateException if {@link #load()} has not been called
     */
    public static String ortDylibAbsolutePath() {
        if (!loaded) throw new IllegalStateException("NativeLoader.load() not called");
        return ortDylibAbsolutePath;
    }

    /**
     * Returns whether the native libraries have been successfully loaded.
     *
     * @return {@code true} if {@link #load()} has completed at least once
     */
    public static boolean isLoaded() {
        return loaded;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osTag = os.contains("linux")   ? "linux"
                     : os.contains("windows") ? "windows"
                     : throwUnsupported("OS", os);
        String archTag = ("amd64".equals(arch) || "x86_64".equals(arch)) ? "x86_64"
                       : throwUnsupported("arch", arch);
        return osTag + "-" + archTag;
    }

    private static String throwUnsupported(String name, String value) {
        throw new UnsatisfiedLinkError("jsyn does not yet support " + name + " '" + value + "'");
    }

    private static String jniLibFileName(String platform) {
        return platform.startsWith("windows") ? "synauson_jni.dll" : "libsynauson_jni.so";
    }

    private static String ortLibFileName(String platform) {
        return platform.startsWith("windows") ? "onnxruntime.dll" : "libonnxruntime.so.1.24.4";
    }

    private static Path extractResource(String resourcePath, Path destDir) {
        ClassLoader cl = NativeLoader.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError(
                    "missing native: " + resourcePath
                    + " — add jsyn-natives-<platform> to your classpath");
            }
            String basename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path dest = destDir.resolve(basename);
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (IOException e) {
            UnsatisfiedLinkError err = new UnsatisfiedLinkError(
                "failed to extract " + resourcePath + ": " + e);
            err.initCause(e);
            throw err;
        }
    }

    private static void registerCleanupOnExit(Path tempDir) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.walk(tempDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ignored) {}
                    });
            } catch (IOException ignored) {}
        }, "jsyn-native-cleanup"));
    }
}
