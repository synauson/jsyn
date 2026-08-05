package com.synauson.jsyn.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JSynTestHelpersResolveSynausonRepoTest {

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty("synausonRepoDir");
    }

    @Test
    void systemPropertyOverrideWinsWhenSet() {
        System.setProperty("synausonRepoDir", "/some/ci/checkout/path");
        Path resolved = JSynTestHelpers.resolveSynausonRepo();
        assertEquals(Path.of("/some/ci/checkout/path").toAbsolutePath(), resolved);
    }

    @Test
    void fallsBackToSiblingLayoutWhenPropertyUnset() {
        System.clearProperty("synausonRepoDir");
        Path resolved = JSynTestHelpers.resolveSynausonRepo();
        // From jsyn/jsyn/ (the Gradle project dir), two levels up reaches the
        // shared parent (~/projects/synauson/), then into the sibling checkout.
        Path expected = Path.of("../../synauson").toAbsolutePath().normalize();
        assertEquals(expected, resolved.normalize());
    }
}
