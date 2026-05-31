package me.sshcrack.mc_talking.client.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.concurrent.CompletableFuture;

/**
 * A positional sound instance that streams synthesized PCM audio.
 */
public class StreamingSpeechSoundInstance extends AbstractSoundInstance {
    private final short[] pcm;
    private final int sampleRate;

    public StreamingSpeechSoundInstance(double x, double y, double z, short[] pcm, int sampleRate, float volume) {
        super(ResourceLocation.fromNamespaceAndPath("mc_talking", "tts_stream"), SoundSource.NEUTRAL, RandomSource.create());
        this.pcm = pcm;
        this.sampleRate = sampleRate;
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = 1.0f;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;

        // Dummy Sound object that tells the engine this is a streaming sound
        this.sound = new Sound(
                this.location,
                rnd -> 1.0f,
                rnd -> 1.0f,
                1,
                Sound.Type.FILE,
                true,
                false,
                16
        );
    }

    @Override
    public WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) {
        // Return a dummy WeighedSoundEvents so the SoundEngine doesn't cancel playback.
        WeighedSoundEvents events = new WeighedSoundEvents(this.location, null);
        events.addSound(this.sound);
        return events;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(new PcmAudioStream(pcm, sampleRate));
    }
}
