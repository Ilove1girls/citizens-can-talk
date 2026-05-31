package me.sshcrack.mc_talking.tts;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.McTalking;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Downloads the Kokoro ONNX model (~311MB) with progress tracking.
 */
public class KokoroModelDownloader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String MODEL_URL =
            "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.onnx";
    private static final long EXPECTED_SIZE = 325532387L; // ~311 MB

    private volatile boolean cancelled = false;

    public void download(Consumer<Float> progress, Consumer<String> status) throws IOException {
        cancelled = false;
        Path dest = KokoroModelManager.getModelPath();
        Files.createDirectories(dest.getParent());
        Path temp = dest.resolveSibling(dest.getFileName() + ".tmp");

        status.accept("Connecting...");
        URL url = new URL(MODEL_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);

        long existingSize = Files.exists(temp) ? Files.size(temp) : 0;
        if (existingSize > 0) {
            conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
        }

        int response = conn.getResponseCode();
        boolean resuming = response == 206;
        if (response != 200 && !resuming) {
            throw new IOException("HTTP " + response);
        }

        long total = resuming ? EXPECTED_SIZE : conn.getContentLengthLong();
        if (total <= 0) total = EXPECTED_SIZE;

        try (InputStream in = conn.getInputStream();
             OutputStream out = new BufferedOutputStream(
                     new FileOutputStream(temp.toFile(), resuming))) {

            byte[] buf = new byte[65536];
            long downloaded = resuming ? existingSize : 0;
            int n;
            long lastUpdate = System.currentTimeMillis();

            while ((n = in.read(buf)) >= 0) {
                if (cancelled) {
                    throw new IOException("Download cancelled");
                }
                out.write(buf, 0, n);
                downloaded += n;

                long now = System.currentTimeMillis();
                if (now - lastUpdate > 200) {
                    float pct = (float) downloaded / total;
                    progress.accept(pct);
                    status.accept(String.format("Downloading: %.1f / %.1f MB (%.0f%%)",
                            downloaded / (1024f * 1024f), total / (1024f * 1024f), pct * 100));
                    lastUpdate = now;
                }
            }
        }

        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
        progress.accept(1.0f);
        status.accept("Download complete!");
        LOGGER.info("[Kokoro] Model downloaded to {}", dest);
    }

    public void cancel() {
        cancelled = true;
    }
}
