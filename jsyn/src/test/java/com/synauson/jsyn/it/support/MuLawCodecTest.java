package com.synauson.jsyn.it.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuLawCodecTest {

    @Test
    void silenceEncodesAndDecodesNearZero() {
        byte encoded = MuLawCodec.encode((short) 0);
        short decoded = MuLawCodec.decode(encoded);
        assertTrue(Math.abs(decoded) < 50, "silence should decode near zero, got " + decoded);
    }

    @Test
    void roundTripPreservesApproximateAmplitude() {
        short original = 10000;
        byte encoded = MuLawCodec.encode(original);
        short decoded = MuLawCodec.decode(encoded);
        // mu-law is lossy (8-bit logarithmic encoding of a 16-bit sample);
        // this tolerance matches the codec's known worst-case quantization
        // error at this amplitude range.
        assertTrue(Math.abs(decoded - original) < 500,
                "mu-law round trip should stay within lossy-compression tolerance, got " + decoded);
    }

    @Test
    void encodeBufferProducesOneBytePerSample() {
        byte[] pcm = new byte[320]; // 160 samples * 2 bytes
        for (int i = 0; i < 160; i++) {
            short s = (short) (10000 * Math.sin(2 * Math.PI * i / 40.0));
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        byte[] encoded = MuLawCodec.encodeBuffer(pcm, 0, pcm.length);

        assertEquals(160, encoded.length);
    }

    @Test
    void decodeBufferProducesTwoBytesPerSample() {
        byte[] ulaw = new byte[160];
        byte[] decoded = MuLawCodec.decodeBuffer(ulaw);
        assertEquals(320, decoded.length);
    }

    @Test
    void encodeBufferAndDecodeBufferRoundTripPreservesAmplitude() {
        short[] originalSamples = {0, 5000, -5000, 10000, -10000, 32000, -32000};
        byte[] pcm = new byte[originalSamples.length * 2];
        for (int i = 0; i < originalSamples.length; i++) {
            pcm[i * 2] = (byte) (originalSamples[i] & 0xFF);
            pcm[i * 2 + 1] = (byte) ((originalSamples[i] >> 8) & 0xFF);
        }

        byte[] encoded = MuLawCodec.encodeBuffer(pcm, 0, pcm.length);
        byte[] decodedPcm = MuLawCodec.decodeBuffer(encoded);

        for (int i = 0; i < originalSamples.length; i++) {
            int lo = decodedPcm[i * 2] & 0xFF;
            int hi = decodedPcm[i * 2 + 1];
            short decoded = (short) ((hi << 8) | lo);
            assertTrue(Math.abs(decoded - originalSamples[i]) < 500,
                    "sample " + i + ": expected near " + originalSamples[i] + ", got " + decoded);
        }
    }
}
