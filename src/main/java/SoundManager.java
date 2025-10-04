package main.java;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename;

public class SoundManager {
    private static long device;
    private static long context;

    private static final Map<String, Integer> buffers = new HashMap<>();
    private static final Map<String, Integer> sources = new HashMap<>();

    // Inicializálás
    public static void init() {
        // alapértelmezett hang eszköz
        device = ALC10.alcOpenDevice((CharSequence) null);
        if (device == NULL) {
            throw new IllegalStateException("Nem sikerült megnyitni az alapértelmezett hang eszközt!");
        }

        // kontextus
        context = ALC10.alcCreateContext(device, new int[]{0});
        if (context == NULL) {
            throw new IllegalStateException("Nem sikerült létrehozni az OpenAL kontextust!");
        }

        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(ALC.createCapabilities(device));

        System.out.println("✅ OpenAL inicializálva");
    }

    // Hang betöltése (.ogg ajánlott)
    public static void loadSound(String name, String path) {
        if (!Files.exists(Paths.get(path))) {
            throw new RuntimeException("Hangfájl nem található: " + path);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);

            ShortBuffer rawAudio = stb_vorbis_decode_filename(path, channelsBuffer, sampleRateBuffer);
            if (rawAudio == null) {
                throw new RuntimeException("Nem sikerült betölteni: " + path);
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);

            int format = -1;
            if (channels == 1) {
                format = AL10.AL_FORMAT_MONO16;
            } else if (channels == 2) {
                format = AL10.AL_FORMAT_STEREO16;
            } else {
                throw new RuntimeException("Nem támogatott csatornaszám: " + channels);
            }

            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, format, rawAudio, sampleRate);

            int source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);

            buffers.put(name, buffer);
            sources.put(name, source);

            System.out.println("🎵 Betöltve: " + name + " (" + path + ")");
        }
    }
    
    public static void setVolume(String name, float volume) {
        Integer source = sources.get(name);
        if (source != null) {
            // hangerő beállítása 0.0f – 1.0f között
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
        }
    }


    // Lejátszás
    public static void play(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcePlay(source);
        }
    }

    // Loop (háttérzene)
    public static void loop(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
            AL10.alSourcePlay(source);
        }
    }

    // Megállítás
    public static void stop(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            AL10.alSourceStop(source);
        }
    }

    // Takarítás
    public static void cleanup() {
        for (int source : sources.values()) {
            AL10.alDeleteSources(source);
        }
        for (int buffer : buffers.values()) {
            AL10.alDeleteBuffers(buffer);
        }
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }
}
