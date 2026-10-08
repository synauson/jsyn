package com.synauson.jsyn.it.support;

/** ITU-T G.711 mu-law codec (RFC 3551 static payload type 0, "PCMU"). */
final class MuLawCodec {
    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;
    private static final int[] SEG_END = {0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF};
    private static final int[] EXP_LUT = {0, 132, 396, 924, 1980, 4092, 8316, 16764};

    private MuLawCodec() {}

    static byte encode(short pcm) {
        int sample = pcm;
        int sign = (sample >> 8) & 0x80;
        if (sign != 0) sample = -sample;
        if (sample > CLIP) sample = CLIP;
        sample += BIAS;
        int exponent = 7;
        for (int i = 0; i < SEG_END.length; i++) {
            if (sample <= SEG_END[i]) {
                exponent = i;
                break;
            }
        }
        int mantissa = (sample >> (exponent + 3)) & 0x0F;
        int ulawByte = ~(sign | (exponent << 4) | mantissa);
        return (byte) ulawByte;
    }

    static short decode(byte ulawByte) {
        int u = ~ulawByte & 0xFF;
        int sign = u & 0x80;
        int exponent = (u >> 4) & 0x07;
        int mantissa = u & 0x0F;
        int sample = EXP_LUT[exponent] + (mantissa << (exponent + 3));
        if (sign != 0) sample = -sample;
        return (short) sample;
    }

    /** Encode {@code length} bytes of signed 16-bit little-endian PCM starting at {@code offset}. */
    static byte[] encodeBuffer(byte[] pcmS16LE, int offset, int length) {
        int frames = length / 2;
        byte[] out = new byte[frames];
        for (int i = 0; i < frames; i++) {
            int lo = pcmS16LE[offset + i * 2] & 0xFF;
            int hi = pcmS16LE[offset + i * 2 + 1];
            short sample = (short) ((hi << 8) | lo);
            out[i] = encode(sample);
        }
        return out;
    }

    /** Decode mu-law bytes back to signed 16-bit little-endian PCM. */
    static byte[] decodeBuffer(byte[] ulaw) {
        byte[] out = new byte[ulaw.length * 2];
        for (int i = 0; i < ulaw.length; i++) {
            short sample = decode(ulaw[i]);
            out[i * 2] = (byte) (sample & 0xFF);
            out[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return out;
    }
}
