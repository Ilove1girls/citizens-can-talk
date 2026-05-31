package me.sshcrack.mc_talking.tts;

import com.mojang.logging.LogUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Downloads and extracts pre-built espeak-ng native libraries for the current platform.
 *
 * <p>Uses the {@code espeakng-loader} release artifacts (by thewh1teagle) which provide
 * self-contained espeak-ng libraries for Windows, Linux, and macOS on both x86_64 and arm64.</p>
 *
 * <p>Extracted files are placed under {@code <game-dir>/mc_talking/native/espeak/} so that
 * JNA can load them via {@code jna.library.path} and the subprocess fallback can find the CLI.</p>
 */
public class EspeakNgNativeDownloader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final String BASE_URL =
            "https://github.com/thewh1teagle/espeakng-loader/releases/download/v0.1.0/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Downloads the platform-specific espeak-ng native library archive if it is not
     * already present in the local native directory.
     */
    public void downloadIfMissing(Consumer<Double> onProgress, Consumer<String> onStatus) throws Exception {
        if (isNativeLibPresent()) {
            LOGGER.info("[EspeakNgNativeDownloader] Native library already present");
            return;
        }

        String platformFile = getPlatformFileName();
        if (platformFile == null) {
            throw new UnsupportedOperationException(
                    "Unsupported platform for espeak-ng native library: "
                            + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        }

        String url = BASE_URL + platformFile;
        Path tarGzPath = TtsModelManager.getBasePath().resolve(platformFile);
        Path extractDir = getExtractDir();

        Files.createDirectories(extractDir);

        onStatus.accept("Downloading espeak-ng native library...");
        LOGGER.info("[EspeakNgNativeDownloader] Downloading from {}", url);
        downloadFile(url, tarGzPath, onProgress);

        onStatus.accept("Extracting espeak-ng native library...");
        extractTarGz(tarGzPath, extractDir);
        Files.deleteIfExists(tarGzPath);

        LOGGER.info("[EspeakNgNativeDownloader] Extracted espeak-ng native library to {}", extractDir);
    }

    /**
     * Returns whether the expected native library file already exists locally.
     */
    public static boolean isNativeLibPresent() {
        return Files.exists(getExpectedLibPath());
    }

    /**
     * Returns the path to the expected native library file for the current platform.
     */
    public static Path getExpectedLibPath() {
        Path extractDir = getExtractDir();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return extractDir.resolve("espeak-ng.dll");
        } else if (os.contains("mac") || os.contains("darwin")) {
            return extractDir.resolve("libespeak-ng.dylib");
        } else {
            return extractDir.resolve("libespeak-ng.so");
        }
    }

    /**
     * Returns the directory where espeak-ng native files are extracted.
     */
    public static Path getExtractDir() {
        return TtsModelManager.getNativeLibPath().resolve("espeak");
    }

    private static String getPlatformFileName() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String platform;
        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                platform = "windows-arm64";
            } else {
                platform = "windows-x86_64";
            }
        } else if (os.contains("mac") || os.contains("darwin")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                platform = "macos-arm64";
            } else {
                platform = "macos-x86_64";
            }
        } else if (os.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                platform = "linux-arm64";
            } else {
                platform = "linux-x86_64";
            }
        } else {
            return null;
        }

        return "espeak-ng-libs-" + platform + ".tar.gz";
    }

    private void downloadFile(String url, Path dest, Consumer<Double> onProgress) throws Exception {
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

    private void extractTarGz(Path tarGz, Path destDir) throws IOException {
        Map<String, String> symlinks = new HashMap<>();

        // First pass: extract regular files, record symlinks
        try (InputStream fi = Files.newInputStream(tarGz);
             GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzi)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.startsWith("espeak-ng-libs/lib/") && !name.startsWith("espeak-ng-libs/bin/")) {
                    continue;
                }
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = name.substring(name.lastIndexOf('/') + 1);

                if (entry.isSymbolicLink()) {
                    String target = entry.getLinkName();
                    if (target != null && !target.isEmpty()) {
                        symlinks.put(fileName, target);
                    }
                } else {
                    Path outPath = destDir.resolve(fileName);
                    Files.createDirectories(outPath.getParent());
                    Files.copy(tarIn, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // Second pass: resolve symlinks by copying from their real targets
        for (Map.Entry<String, String> e : symlinks.entrySet()) {
            Path linkPath = destDir.resolve(e.getKey());
            if (Files.exists(linkPath)) {
                continue; // already present
            }

            String target = e.getValue();
            // Target may be relative (e.g. "libespeak-ng.so.1.52.0") — take just the file name
            Path targetPath = destDir.resolve(Path.of(target).getFileName().toString());
            if (Files.exists(targetPath)) {
                Files.copy(targetPath, linkPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("[EspeakNgNativeDownloader] Resolved symlink {} -> {}", linkPath, targetPath);
            } else {
                LOGGER.warn("[EspeakNgNativeDownloader] Symlink target not found: {} -> {}", targetPath, target);
            }
        }
    }
}
