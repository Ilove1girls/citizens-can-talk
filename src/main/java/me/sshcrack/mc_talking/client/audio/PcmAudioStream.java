package me.sshcrack.mc_talking.client.audio;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * An {@link AudioStream} backed by a pre-synthesized PCM short array.
 * Chunks the data across multiple {@link #read(int)} calls for OpenAL streaming.
 */
public class PcmAudioStream implements AudioStream {
    private final ByteBuffer buffer;
    private final AudioFormat format;
    private boolean closed = false;

    public PcmAudioStream(short[] pcm, int sampleRate) {
        this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        this.buffer = ByteBuffer.allocateDirect(pcm.length * 2)
                .order(ByteOrder.nativeOrder());
        for (short s : pcm) {
            this.buffer.putShort(s);
        }
        this.buffer.flip();
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (closed || !buffer.hasRemaining()) {
            return ByteBuffer.allocateDirect(0);
        }

        int toRead = Math.min(size, buffer.remaining());
        ByteBuffer slice = buffer.slice();
        slice.limit(toRead);
        buffer.position(buffer.position() + toRead);
        return slice;
    }

    @Override
    public void close() {
        closed = true;
    }
}
