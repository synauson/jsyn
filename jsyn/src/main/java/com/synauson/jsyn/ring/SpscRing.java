package com.synauson.jsyn.ring;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Internal: not part of the stable jsyn API. Do not use directly from application code.
 *
 * <p>Java-side view of the shared-memory SPSC ring buffer.
 *
 * <p>Wraps a direct {@link ByteBuffer} whose memory is owned by the native participant.
 * Uses {@link VarHandle} acquire/release semantics on the writer and reader position
 * counters to maintain the producer-consumer ordering guarantee without a full fence.
 *
 * <p>Thread safety: the Java side is the sole writer to the ingress ring and the sole
 * reader from the egress ring. The Rust side fills the complementary role. Only one
 * Java thread may call {@link #write} at a time; only one Java thread may call
 * {@link #read} at a time.
 *
 * @since 0.1.0
 */
public final class SpscRing {
    private static final VarHandle LONG_VIEW =
        MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.nativeOrder());

    private final ByteBuffer buffer;
    private final int capacity;
    private final int mask;

    /**
     * Wrap a direct {@link ByteBuffer} over a ring buffer's shared memory.
     *
     * @param buffer a direct {@link ByteBuffer} over the ring's shared memory;
     *               must be direct and have the correct layout per {@link RingLayout}
     * @throws IllegalArgumentException if {@code buffer} is not direct
     * @throws IllegalStateException    if the capacity field in the ring header is invalid
     *                                  (not a positive power of two, or exceeds {@link Integer#MAX_VALUE})
     */
    public SpscRing(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("ring ByteBuffer must be direct");
        }
        this.buffer = buffer.duplicate().order(ByteOrder.nativeOrder());
        long cap = (long) LONG_VIEW.get(this.buffer, RingLayout.CAPACITY_OFFSET);
        if (cap <= 0 || (cap & (cap - 1)) != 0 || cap > Integer.MAX_VALUE) {
            throw new IllegalStateException("invalid ring capacity " + cap);
        }
        this.capacity = (int) cap;
        this.mask = capacity - 1;
    }

    /**
     * Returns the ring's data capacity in bytes.
     *
     * @return capacity in bytes; always a power of two
     */
    public int capacity() { return capacity; }

    /**
     * Returns the format ID stored in the ring header. Maps to
     * {@link com.synauson.jsyn.NativeAudioFormat#fromId(int)}.
     *
     * @return the stable format ID written into the header by the native side
     */
    public int formatId() {
        return (int) (long) LONG_VIEW.get(buffer, RingLayout.FORMAT_ID_OFFSET);
    }

    /**
     * Write up to {@code len} bytes from {@code src} into the ring.
     *
     * @param src source byte array
     * @param off offset into {@code src}
     * @param len number of bytes to write
     * @return bytes actually written (may be less than {@code len} if the ring is near full)
     */
    public int write(byte[] src, int off, int len) {
        long writer = (long) LONG_VIEW.get(buffer, RingLayout.WRITER_POS_OFFSET);
        long reader = (long) LONG_VIEW.getAcquire(buffer, RingLayout.READER_POS_OFFSET);
        int used = (int) (writer - reader);
        int free = capacity - used;
        int toWrite = Math.min(len, free);
        if (toWrite == 0) return 0;
        int offset = (int) (writer & mask);
        int tail = capacity - offset;
        if (toWrite <= tail) {
            putBytes(RingLayout.DATA_OFFSET + offset, src, off, toWrite);
        } else {
            putBytes(RingLayout.DATA_OFFSET + offset, src, off, tail);
            putBytes(RingLayout.DATA_OFFSET, src, off + tail, toWrite - tail);
        }
        LONG_VIEW.setRelease(buffer, RingLayout.WRITER_POS_OFFSET, writer + toWrite);
        return toWrite;
    }

    /**
     * Read up to {@code len} bytes from the ring into {@code dst}.
     *
     * @param dst destination byte array
     * @param off offset into {@code dst}
     * @param len maximum bytes to read
     * @return bytes actually read (may be less than {@code len} if the ring has fewer available)
     */
    public int read(byte[] dst, int off, int len) {
        long writer = (long) LONG_VIEW.getAcquire(buffer, RingLayout.WRITER_POS_OFFSET);
        long reader = (long) LONG_VIEW.get(buffer, RingLayout.READER_POS_OFFSET);
        int available = (int) (writer - reader);
        int toRead = Math.min(len, available);
        if (toRead == 0) return 0;
        int offset = (int) (reader & mask);
        int tail = capacity - offset;
        if (toRead <= tail) {
            getBytes(RingLayout.DATA_OFFSET + offset, dst, off, toRead);
        } else {
            getBytes(RingLayout.DATA_OFFSET + offset, dst, off, tail);
            getBytes(RingLayout.DATA_OFFSET, dst, off + tail, toRead - tail);
        }
        LONG_VIEW.setRelease(buffer, RingLayout.READER_POS_OFFSET, reader + toRead);
        return toRead;
    }

    private void putBytes(int bufOff, byte[] src, int srcOff, int len) {
        ByteBuffer dup = buffer.duplicate();
        dup.position(bufOff);
        dup.put(src, srcOff, len);
    }

    private void getBytes(int bufOff, byte[] dst, int dstOff, int len) {
        ByteBuffer dup = buffer.duplicate();
        dup.position(bufOff);
        dup.get(dst, dstOff, len);
    }
}
