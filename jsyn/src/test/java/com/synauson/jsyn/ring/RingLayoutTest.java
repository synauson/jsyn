package com.synauson.jsyn.ring;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingLayoutTest {
    @Test
    void offsetsMatchRustSide() {
        assertEquals(0x000, RingLayout.WRITER_POS_OFFSET);
        assertEquals(0x040, RingLayout.READER_POS_OFFSET);
        assertEquals(0x080, RingLayout.CAPACITY_OFFSET);
        assertEquals(0x088, RingLayout.FORMAT_ID_OFFSET);
        assertEquals(0x090, RingLayout.SAMPLE_RATE_OFFSET);
        assertEquals(0x098, RingLayout.BYTES_PER_FRAME_OFFSET);
        assertEquals(0x0a0, RingLayout.OVERRUN_COUNT_OFFSET);
        assertEquals(0x0a8, RingLayout.UNDERRUN_COUNT_OFFSET);
        assertEquals(0x100, RingLayout.DATA_OFFSET);
    }
}
