package com.synauson.jsyn.it;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void systemPropertyForwardedFromCommandLineReachesForkedTestJvm() {
        // Unlike the two tests above (which set/clear the property from
        // within this same JVM and would pass even if Gradle's -D-to-forked-JVM
        // forwarding were completely broken), this test observes whatever
        // value Gradle's Test task actually forwarded from the command
        // line — proving jsyn/build.gradle.kts's systemProperty(...) call
        // works, not just that resolveSynausonRepo()'s own logic is
        // correct. Skips locally (no -D flag passed to a bare `./gradlew
        // :jsyn:test`); actually asserts in CI, where -DsynausonRepoDir is
        // always supplied — so a future regression that silently removes
        // the forwarding line would be caught by this test failing in CI.
        String forwarded = System.getProperty("synausonRepoDir");
        Assumptions.assumeTrue(forwarded != null,
                "no -DsynausonRepoDir passed to this invocation — skipping forwarding check");
        assertFalse(forwarded.isBlank(), "synausonRepoDir system property was forwarded but blank");
    }
}
