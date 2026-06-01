package me.sshcrack.mc_talking.stt;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.McTalking;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Downloads the Whisper Base.EN model files for Sherpa-ONNX STT.
 * Thread-safe: uses a temp file and atomic move to prevent corruption
 * from concurrent downloads.
 */
public class WhisperModelDownloader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Object DOWNLOAD_LOCK = new Object();
    private static final String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Downloads and extracts the Whisper model if not already present.
     * Self-healing: retries once with cleanup on any failure.
     *
     * @param onProgress optional callback receiving 0.0–1.0 download progress
     * @param onStatus   optional callback receiving status text updates
     * @throws Exception if download or extraction fails (after retry)
     */
    public void downloadIfMissing(Consumer<Float> onProgress, Consumer<String> onStatus) throws Exception {
        if (SttModelManager.isModelReady()) {
            LOGGER.info("[STT] Whisper model already present");
            if (onStatus != null) onStatus.accept("Whisper model already present");
            return;
        }

        synchronized (DOWNLOAD_LOCK) {
            // Double-check after acquiring lock
            if (SttModelManager.isModelReady()) {
                LOGGER.info("[STT] Whisper model appeared while waiting for lock");
                if (onStatus != null) onStatus.accept("Whisper model already present");
                return;
            }

            try {
                doDownload(onProgress, onStatus);
            } catch (Exception e) {
                LOGGER.error("[STT] Download/extraction failed — cleaning up and retrying once...", e);
                cleanupPartialFiles();
                doDownload(onProgress, onStatus);
            }
        }
    }

    private void doDownload(Consumer<Float> onProgress, Consumer<String> onStatus) throws Exception {
        Path modelsDir = SttModelManager.getModelsPath();
        Files.createDirectories(modelsDir);

        Path tarBz2 = modelsDir.resolve("sherpa-onnx-whisper-base.en.tar.bz2");
        Path tempTarBz2 = modelsDir.resolve("sherpa-onnx-whisper-base.en.tar.bz2.tmp");

        // If a previous download left a corrupt tar, delete it
        Files.deleteIfExists(tarBz2);

        if (onStatus != null) onStatus.accept("Downloading Whisper Base.EN model (~150 MB)...");
        LOGGER.info("[STT] Downloading Whisper Base.EN model (~150 MB)...");
        downloadWithProgress(tempTarBz2, onProgress);

        // Atomic move: prevents other threads from seeing a partial file
        Files.move(tempTarBz2, tarBz2, StandardCopyOption.REPLACE_EXISTING);

        if (onStatus != null) onStatus.accept("Extracting Whisper model...");
        LOGGER.info("[STT] Extracting model...");
        extractTarBz2(tarBz2, modelsDir);

        Files.deleteIfExists(tarBz2);

        if (SttModelManager.isModelReady()) {
            LOGGER.info("[STT] Whisper model ready at {}", SttModelManager.getWhisperPath());
            if (onStatus != null) onStatus.accept("Whisper model ready");
        } else {
            throw new IOException("Model extraction succeeded but expected files not found in " + SttModelManager.getWhisperPath());
        }
    }

    private void cleanupPartialFiles() {
        Path modelsDir = SttModelManager.getModelsPath();
        try {
            Files.deleteIfExists(modelsDir.resolve("sherpa-onnx-whisper-base.en.tar.bz2"));
            Files.deleteIfExists(modelsDir.resolve("sherpa-onnx-whisper-base.en.tar.bz2.tmp"));
        } catch (IOException e) {
            LOGGER.warn("[STT] Failed to clean up partial Whisper files", e);
        }
    }

    /**
     * Convenience overload with no callbacks.
     */
    public void downloadIfMissing() throws Exception {
        downloadIfMissing(null, null);
    }

    private void downloadWithProgress(Path dest, Consumer<Float> onProgress) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(MODEL_URL))
                .header("Accept", "*/*")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + MODEL_URL);
        }

        long totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int read;
            long lastLoggedMb = -1;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (totalBytes > 0) {
                    long mb = downloaded / (1024 * 1024);
                    if (mb > lastLoggedMb) {
                        lastLoggedMb = mb;
                        LOGGER.info("[STT] Downloaded {} MB / {} MB", mb, totalBytes / (1024 * 1024));
                    }
                    if (onProgress != null) {
                        onProgress.accept((float) downloaded / totalBytes);
                    }
                }
            }
        }
    }

    /**
     * Extracts a tar.bz2 using manual buffered copy.
     * Avoids Files.copy() which can hang on Windows with TarArchiveInputStream.
     */
    private void extractTarBz2(Path tarBz2, Path destDir) throws Exception {
        try (java.io.InputStream fi = Files.newInputStream(tarBz2);
             org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream bzIn = new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(fi);
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tarIn = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(bzIn)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                    continue;
                }
                Files.createDirectories(outPath.getParent());
                long size = entry.getSize();
                LOGGER.info("[STT] Extracting {} ({} bytes)...", entry.getName(), size);
                long start = System.currentTimeMillis();

                // Manual buffered copy — more reliable than Files.copy() on Windows
                try (OutputStream out = Files.newOutputStream(outPath)) {
                    byte[] buf = new byte[65536];
                    long written = 0;
                    int read;
                    while (written < size && (read = tarIn.read(buf, 0, (int) Math.min(buf.length, size - written))) != -1) {
                        if (read > 0) {
                            out.write(buf, 0, read);
                            written += read;
                        }
                    }
                }
                LOGGER.info("[STT] Extracted {} in {} ms", entry.getName(), System.currentTimeMillis() - start);
            }
        }
    }
}
