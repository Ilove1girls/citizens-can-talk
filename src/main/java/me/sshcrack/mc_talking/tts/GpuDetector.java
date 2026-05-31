package me.sshcrack.mc_talking.tts;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Simple GPU detection utility.
 */
public class GpuDetector {
    private static final Boolean HAS_NVIDIA = detectNvidia();

    public static boolean hasNvidiaGpu() {
        return Boolean.TRUE.equals(HAS_NVIDIA);
    }

    private static Boolean detectNvidia() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"nvidia-smi", "-L"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.readLine() != null;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
