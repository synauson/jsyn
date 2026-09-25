package com.synauson.jsyn.internal;

import com.synauson.jsyn.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArgsTest {
    @Test
    void notNullReturnsValue() {
        String v = "x";
        assertSame(v, Args.notNull(v, "v"));
    }

    @Test
    void notNullThrowsInvalidArgumentNamingTheArgument() {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
            () -> Args.notNull(null, "participantId"));
        assertEquals("participantId must not be null", e.getMessage());
    }

    @Test
    void requiredPassesWhenNothingIsMissing() {
        assertDoesNotThrow(() -> Args.required("T").field("a", "x").field("b", 1).validate());
    }

    @Test
    void requiredReportsEveryMissingFieldInOrder() {
        InvalidArgumentException e = assertThrows(InvalidArgumentException.class,
            () -> Args.required("SipRemoteMedia")
                .field("remoteIp", null)
                .field("present", "x")
                .field("codec", null)
                .validate());
        assertEquals("SipRemoteMedia requires remoteIp, codec", e.getMessage());
    }
}
