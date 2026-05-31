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

/**
 * Downloads the Whisper Base.EN model files for Sherpa-ONNX STT.
 * Server-friendly: no GUI, logs progress to console.
 */
public class WhisperModelDownloader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.en.tar.bz2";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Downloads and extracts the Whisper model if not already present.
     *
     * @throws Exception if download or extraction fails
     */
    public void downloadIfMissing() throws Exception {
        if (SttModelManager.isModelReady()) {
            LOGGER.info("[STT] Whisper model already present");
            return;
        }

        Path modelsDir = SttModelManager.getModelsPath();
        Files.createDirectories(modelsDir);

        Path tarBz2 = modelsDir.resolve("sherpa-onnx-whisper-base.en.tar.bz2");

        LOGGER.info("[STT] Downloading Whisper Base.EN model (~150 MB)...");
        downloadWithProgress(tarBz2);

        LOGGER.info("[STT] Extracting model...");
        extractTarBz2(tarBz2, modelsDir);

        Files.deleteIfExists(tarBz2);

        if (SttModelManager.isModelReady()) {
            LOGGER.info("[STT] Whisper model ready at {}", SttModelManager.getWhisperPath());
        } else {
            throw new IOException("Model extraction succeeded but expected files not found in " + SttModelManager.getWhisperPath());
        }
    }

    private void downloadWithProgress(Path dest) throws Exception {
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
                }
            }
        }
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
                    Files.copy(tarIn, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
