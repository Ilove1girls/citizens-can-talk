package me.sshcrack.mc_talking.tts;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.GZIPInputStream;

/**
 * Downloads and extracts TTS model files and native libraries.
 */
public class ModelDownloader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public static String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-high.tar.bz2";
    public static String NATIVE_BASE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.2/";
    public static String ESPEAK_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2";

    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    public void downloadAll(Consumer<Double> onProgress, Consumer<String> onStatus) throws Exception {
        Path base = TtsModelManager.getBasePath();
        Files.createDirectories(base);

        // Extract bundled tokens.txt
        onStatus.accept("Extracting tokens...");
        extractTokens();
        onProgress.accept(0.05);

        // Download espeak-ng-data
        if (!Files.exists(TtsModelManager.getEspeakDataPath())) {
            onStatus.accept("Downloading espeak-ng-data...");
            downloadEspeakNgData();
        }
        onProgress.accept(0.15);

        // Download espeak-ng native library (needed for Kokoro phonemization)
        if (!EspeakNgNativeDownloader.isNativeLibPresent()) {
            onStatus.accept("Downloading espeak-ng native library...");
            new EspeakNgNativeDownloader().downloadIfMissing(p -> {}, s -> {});
        }
        onProgress.accept(0.20);

        // Download native library JAR and extract .so/.dll
        if (!TtsModelManager.isNativeLibReady()) {
            onStatus.accept("Downloading native libraries...");
            downloadAndExtractNativeLib();
        }
        onProgress.accept(0.30);

        // Download ONNX model
        if (!Files.exists(TtsModelManager.getModelPath())) {
            if (MODEL_URL.isBlank()) {
                // Try to copy from local modding directory for development
                Path localModel = Paths.get("/home/sky/Documents/Projects/Modding/en_US-libritts-high.onnx");
                if (Files.exists(localModel)) {
                    onStatus.accept("Copying model from local files...");
                    Files.copy(localModel, TtsModelManager.getModelPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    throw new IllegalStateException("Model URL not configured and local model not found.");
                }
            } else {
                onStatus.accept("Downloading voice model (130 MB)...");
                downloadWithProgress(MODEL_URL, TtsModelManager.getModelPath(), p -> {
                    double overall = 0.30 + p * 0.70;
                    onProgress.accept(overall);
                });
            }
        }
        onProgress.accept(1.0);
        onStatus.accept("Ready");
    }

    private void extractTokens() throws IOException {
        Path target = TtsModelManager.getTokensPath();
        if (Files.exists(target)) return;

        Files.createDirectories(target.getParent());
        try (InputStream in = ModelDownloader.class.getResourceAsStream("/assets/mc_talking/tts/tokens.txt")) {
            if (in == null) {
                throw new IOException("Bundled tokens.txt not found in mod resources");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadEspeakNgData() throws Exception {
        Path tarBz2 = TtsModelManager.getBasePath().resolve("espeak-ng-data.tar.bz2");
        downloadWithProgress(ESPEAK_URL, tarBz2, p -> {});

        Path extractDir = TtsModelManager.getEspeakDataPath().getParent();
        Files.createDirectories(extractDir);

        extractTarBz2(tarBz2, extractDir);
        Files.deleteIfExists(tarBz2);
    }

    private void extractTarBz2(Path tarBz2, Path destDir) throws Exception {
        try (java.io.InputStream fi = Files.newInputStream(tarBz2);
             org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream bzIn = new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(fi);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(tarIn, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void downloadAndExtractNativeLib() throws Exception {
        String platform = detectPlatform();
        String jarName = "sherpa-onnx-native-lib-" + platform + "-v1.13.2.jar";
        String url = NATIVE_BASE_URL + jarName;
        Path jarPath = TtsModelManager.getNativeJarPath();
        Files.createDirectories(jarPath.getParent());
        downloadWithProgress(url, jarPath, p -> {});

        Path extractDir = TtsModelManager.getNativeLibPath();
        Files.createDirectories(extractDir);

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")) {
                    Path out = extractDir.resolve(Paths.get(name).getFileName().toString());
                    try (InputStream in = jarFile.getInputStream(entry);
                         OutputStream outStream = Files.newOutputStream(out)) {
                        in.transferTo(outStream);
                    }
                }
            }
        }

        Files.deleteIfExists(jarPath);
    }

    private void downloadWithProgress(String url, Path dest, Consumer<Double> onProgress) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "*/*")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0) {
                    onProgress.accept((double) downloaded / totalBytes);
                }
            }
        }
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String osName;
        if (os.contains("win")) {
            osName = "win";
        } else if (os.contains("mac") || os.contains("darwin")) {
            osName = "osx";
        } else {
            osName = "linux";
        }

        String archName;
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            archName = "aarch64";
        } else {
            archName = "x64";
        }

        return osName + "-" + archName;
    }



}