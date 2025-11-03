package main.java.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_decode_filename;

public class SoundManager {
    private static long device;
    private static long context;
    private static boolean initialized = false;

    private static final Map<String, Integer> buffers = new HashMap<>();
    private static final Map<String, Integer> sources = new HashMap<>();
    private static final int POOL_SIZE = 32; 
    private static final List<Integer> sourcePool = new ArrayList<>();
    private static int currentPoolIndex = 0;

    // Inicializálás
    public static void init() {
        if (initialized) return;
        initialized = true;
        device = ALC10.alcOpenDevice((CharSequence) null);
        if (device == NULL) {
            throw new IllegalStateException("Nem sikerült megnyitni az alapértelmezett hang eszközt!");
        }

        context = ALC10.alcCreateContext(device, new int[]{0});
        if (context == NULL) {
            throw new IllegalStateException("Nem sikerült létrehozni az OpenAL kontextust!");
        }

        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(ALC.createCapabilities(device));

        System.out.println("OpenAL inicializálva");
        AL10.alDistanceModel(AL10.AL_NONE);
        
        for (int i = 0; i < POOL_SIZE; i++) {
            int source = AL10.alGenSources();
            checkError("alGenSources (pool)");
            sourcePool.add(source);
        }
        System.out.println("SoundManager source pool létrehozva " + POOL_SIZE + " forrással.");
    }

    // Hang betöltése (.ogg)
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

            int format;
            if (channels == 1) {
                format = AL10.AL_FORMAT_MONO16;
            } else if (channels == 2) {
                format = AL10.AL_FORMAT_STEREO16;
            } else {
                throw new RuntimeException("Nem támogatott csatornaszám: " + channels);
            }

            int buffer = AL10.alGenBuffers();
            checkError("alGenBuffers");
            AL10.alBufferData(buffer, format, rawAudio, sampleRate);
            checkError("alBufferData");

            int source = AL10.alGenSources();
            checkError("alGenSources");
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            checkError("alSourcei (buffer bind)");

            buffers.put(name, buffer);
            sources.put(name, source);

            System.out.println("🎵 Betöltve: " + name + " (" + path + 
                               ") | Buffer ID=" + buffer + " Source ID=" + source);
        }
    }

    public static void setVolume(String name, float volume) {
        Integer source = sources.get(name);
        if (source != null) {
            volume = Math.max(0.0f, Math.min(1.0f, volume));
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
            checkError("alSourcef (gain)");
        }
    }

    // Lejátszás
    public static void play(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            System.out.println("▶️ play('" + name + "') Source=" + source);
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            checkError("alSourcei (looping=false)");
            AL10.alSourcePlay(source);
            checkError("alSourcePlay");
        } else {
            System.out.println("❌ play('" + name + "') - nincs forrás!");
        }
    }

    // Loop (háttérzene)
    public static void loop(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            System.out.println("🔁 loop('" + name + "') Source=" + source);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
            checkError("alSourcei (looping=true)");
            AL10.alSourcePlay(source);
            checkError("alSourcePlay (loop)");
        }
    }

    // Megállítás
    public static void stop(String name) {
        Integer source = sources.get(name);
        if (source != null) {
            System.out.println("⏹ stop('" + name + "') Source=" + source);
            AL10.alSourceStop(source);
            checkError("alSourceStop");
        }
    }
    
    // Ellenőrzi, hogy a hang éppen szól-e
    public static boolean isPlaying(String name) {
        Integer source = sources.get(name);
        if (source == null) return false;

        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        checkError("alGetSourcei (state)");
        return state == AL10.AL_PLAYING;
    }
    
 // Többszörös lejátszás ugyanabból a hangból
    public static void playOverlap(String name) {
        Integer buffer = buffers.get(name);
        if (buffer == null) {
            System.out.println("playOverlap('" + name + "') - nincs buffer!");
            return;
        }
        int source = findFreeSource();
        float volume = 1.0f; 
        Integer dedicatedSource = sources.get(name);
        if (dedicatedSource != null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer volBuffer = stack.mallocFloat(1);
                AL10.alGetSourcef(dedicatedSource, AL10.AL_GAIN, volBuffer);
                volume = volBuffer.get(0);
            }
        }
        
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, volume);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcePlay(source);
        checkError("alSourcePlay (overlap)");
    }

    private static int findFreeSource() {
        for (int i = 0; i < POOL_SIZE; i++) {
            currentPoolIndex = (currentPoolIndex + 1) % POOL_SIZE;
            int source = sourcePool.get(currentPoolIndex);
            
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            checkError("alGetSourcei (state for pool)");
            
            if (state == AL10.AL_STOPPED) {
                AL10.alSourcei(source, AL10.AL_BUFFER, 0); 
                checkError("alSourcei (buffer detach)");
                return source;
            }
        }
        currentPoolIndex = (currentPoolIndex + 1) % POOL_SIZE;
        int sourceToSteal = sourcePool.get(currentPoolIndex);
        
        AL10.alSourceStop(sourceToSteal); 
        checkError("alSourceStop (steal)");
        AL10.alSourcei(sourceToSteal, AL10.AL_BUFFER, 0); 
        checkError("alSourcei (buffer detach steal)");
        
        return sourceToSteal;
    }


    // Takarítás
    public static void cleanup() {
        if (!initialized) return;
        initialized = false;
        for (int source : sources.values()) {
            AL10.alDeleteSources(source);
            checkError("alDeleteSources");
        }
        for (int source : sourcePool) {
            AL10.alDeleteSources(source);
        }
        sourcePool.clear();
        checkError("alDeleteSources (pool)");
        for (int buffer : buffers.values()) {
            AL10.alDeleteBuffers(buffer);
            checkError("alDeleteBuffers");
        }
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }

    // --- Segéd függvény hibákhoz ---
    private static void checkError(String msg) {
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            System.out.println("⚠️ OpenAL hiba (" + msg + "): " + err);
        }
    }
}
