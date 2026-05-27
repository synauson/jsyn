package com.synauson.jsyn;

/**
 * Audio format for in-process native participant I/O.
 *
 * <p>Enum names match the JSON wire format expected by the Rust serde deserialiser
 * ({@code serde(rename_all = "SCREAMING_SNAKE_CASE")} on the Rust {@code NativeAudioFormat}
 * enum). Wire values: PCM_S16LE8K_MONO, PCM_S16LE16K_MONO, PCM_S16LE48K_MONO,
 * G711_ULAW8K, G711_ALAW8K.
 *
 * <p>Numeric IDs are stable across versions and match the ring-header {@code format_id} field.
 *
 * @since 0.1.0
 */
public enum NativeAudioFormat {
    PCM_S16LE8K_MONO(0,  8_000,  2),
    PCM_S16LE16K_MONO(1, 16_000, 2),
    PCM_S16LE48K_MONO(2, 48_000, 2),
    G711_ULAW8K(3,       8_000, 1),
    G711_ALAW8K(4,       8_000, 1);

    private final int id;
    private final int sampleRate;
    private final int bytesPerFrame;

    NativeAudioFormat(int id, int sampleRate, int bytesPerFrame) {
        this.id = id;
        this.sampleRate = sampleRate;
        this.bytesPerFrame = bytesPerFrame;
    }

    /**
     * Stable numeric ID matching the Rust {@code NativeAudioFormat::id()} method.
     *
     * @return numeric format ID written into the ring header
     */
    public int id()            { return id; }

    /**
     * PCM sample rate in Hz.
     *
     * @return sample rate (one of {@code 8000}, {@code 16000}, {@code 48000})
     */
    public int sampleRate()    { return sampleRate; }

    /**
     * Bytes consumed per mono sample.
     *
     * @return {@code 2} for PCM s16le formats, {@code 1} for G.711 formats
     */
    public int bytesPerFrame() { return bytesPerFrame; }

    /**
     * Number of bytes needed for a 20 ms frame at this format's sample rate.
     *
     * @return frame size in bytes (sample rate / 50 * bytes per frame)
     */
    public int bytesPer20ms()  { return sampleRate / 50 * bytesPerFrame; }

    /**
     * Returns the enum constant with the given numeric ID.
     *
     * @param id stable format ID (see {@link #id()})
     * @return the matching enum constant
     * @throws IllegalArgumentException if no constant has that ID
     */
    public static NativeAudioFormat fromId(int id) {
        for (NativeAudioFormat f : values()) {
            if (f.id == id) return f;
        }
        throw new IllegalArgumentException("unknown NativeAudioFormat id: " + id);
    }
}
