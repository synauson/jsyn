package com.synauson.jsyn.ring;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Memory layout constants for the lock-free SPSC ring buffer.
 *
 * <p>Mirror of {@code synauson-core/src/participants/native/layout.rs} constants. These
 * offsets and their byte widths MUST stay in sync with the Rust definition. Each field
 * occupies 8 bytes (u64 in Rust); the data region starts at {@link #DATA_OFFSET} =
 * 0x100 (256 bytes into the allocation, leaving room for the 8 header fields plus
 * padding to a cache-line boundary).
 *
 * @since 0.1.0
 */
public final class RingLayout {
    private RingLayout() {}

    /** Byte offset of the writer position counter (u64, monotonically increasing). */
    public static final int WRITER_POS_OFFSET      = 0x000;
    /** Byte offset of the reader position counter (u64, monotonically increasing). */
    public static final int READER_POS_OFFSET      = 0x040;
    /** Byte offset of the capacity field (u64, power-of-two bytes). */
    public static final int CAPACITY_OFFSET        = 0x080;
    /** Byte offset of the format ID field (u64, maps to {@link com.synauson.jsyn.NativeAudioFormat#id()}). */
    public static final int FORMAT_ID_OFFSET       = 0x088;
    /** Byte offset of the sample rate field (u64, Hz). */
    public static final int SAMPLE_RATE_OFFSET     = 0x090;
    /** Byte offset of the bytes-per-frame field (u64). */
    public static final int BYTES_PER_FRAME_OFFSET = 0x098;
    /** Byte offset of the overrun counter (u64, incremented by writer on overflow). */
    public static final int OVERRUN_COUNT_OFFSET   = 0x0a0;
    /** Byte offset of the underrun counter (u64, incremented by reader on underflow). */
    public static final int UNDERRUN_COUNT_OFFSET  = 0x0a8;
    /** Byte offset of the first data byte. */
    public static final int DATA_OFFSET            = 0x100;
}
