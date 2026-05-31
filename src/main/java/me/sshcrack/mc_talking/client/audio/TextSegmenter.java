package me.sshcrack.mc_talking.client.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits AI-generated text into natural speech segments at punctuation boundaries.
 * Splits at periods, exclamation marks, and question marks first.
 * For very long sentences, also splits at commas to keep synthesis fast.
 */
public class TextSegmenter {
    /** Hard ceiling on words per segment. Longer sentences get split at commas. */
    private static final int MAX_WORDS_PER_SEGMENT = 30;

    /**
     * Splits text into natural segments for streaming TTS playback.
     * Prioritizes sentence boundaries, then commas for long sentences.
     *
     * @param text the full AI response
     * @return list of natural speech segments
     */
    public static List<String> split(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        String trimmed = text.trim();

        // First pass: split by sentence boundaries (. ! ?)
        List<String> sentences = splitIntoSentences(trimmed);

        // Second pass: split long sentences at commas
        for (String sentence : sentences) {
            if (countWords(sentence) > MAX_WORDS_PER_SEGMENT) {
                result.addAll(splitAtCommas(sentence));
            } else {
                result.add(sentence);
            }
        }

        return result;
    }

    /**
     * Splits text at sentence boundaries while preserving punctuation.
     */
    private static List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);

            if (c == '.' || c == '!' || c == '?') {
                // Check if this is actually an end-of-sentence
                boolean isEnd = (i + 1 >= text.length()) || (text.charAt(i + 1) == ' ');
                if (isEnd) {
                    String sentence = current.toString().trim();
                    if (!sentence.isEmpty()) {
                        sentences.add(sentence);
                    }
                    current.setLength(0);
                }
            }
        }

        // Any trailing text without terminal punctuation
        if (!current.isEmpty()) {
            String trailing = current.toString().trim();
            if (!trailing.isEmpty()) {
                sentences.add(trailing);
            }
        }

        return sentences;
    }

    /**
     * Splits a long sentence at commas to create natural pauses.
     * Groups short comma-separated clauses together if they're under the word limit.
     */
    private static List<String> splitAtCommas(String sentence) {
        List<String> result = new ArrayList<>();
        String[] parts = sentence.split(",");

        StringBuilder current = new StringBuilder();
        int currentWords = 0;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) continue;

            // Re-add comma if not the last part
            if (i < parts.length - 1) {
                part = part + ",";
            }

            int partWords = countWords(part);

            if (currentWords == 0) {
                current.append(part);
                currentWords = partWords;
            } else if (currentWords + partWords <= MAX_WORDS_PER_SEGMENT) {
                current.append(" ").append(part);
                currentWords += partWords;
            } else {
                String seg = current.toString().trim();
                if (!seg.isEmpty()) {
                    result.add(seg);
                }
                current.setLength(0);
                current.append(part);
                currentWords = partWords;
            }
        }

        if (currentWords > 0) {
            String seg = current.toString().trim();
            if (!seg.isEmpty()) {
                result.add(seg);
            }
        }

        return result;
    }

    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        int count = 0;
        for (String word : text.split("\\s+")) {
            if (!word.isEmpty()) count++;
        }
        return count;
    }
}
