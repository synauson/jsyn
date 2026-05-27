package com.synauson.jsyn.internal;

import com.synauson.jsyn.exception.NativeResourceClosedException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeResourceTest {
    @Test
    void closeIsIdempotent() {
        boolean[] freed = {false};
        TestResource r = new TestResource(() -> freed[0] = true);
        r.close();
        r.close(); // second close must not throw and must not call free twice
        assertTrue(freed[0]);
    }

    @Test
    void isClosedReflectsState() {
        TestResource r = new TestResource(() -> {});
        assertFalse(r.isClosed());
        r.close();
        assertTrue(r.isClosed());
    }

    @Test
    void requireOpenThrowsAfterClose() {
        TestResource r = new TestResource(() -> {});
        r.close();
        assertThrows(NativeResourceClosedException.class, r::useIt);
    }

    @Test
    void requireOpenSucceedsWhenOpen() {
        TestResource r = new TestResource(() -> {});
        assertDoesNotThrow(r::useIt);
    }

    static class TestResource extends NativeResource {
        TestResource(Runnable free) { super(free); }
        void useIt() { requireOpen(); }
    }
}
