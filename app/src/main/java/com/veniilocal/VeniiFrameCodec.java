package com.veniilocal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** 4-byte big endian length + Base64(AES-128-ECB / zero-padded JSON), as observed. */
public final class VeniiFrameCodec {
    // Publicly extractable compatibility key from old APK; never store session auth or robot MAC.
    private static final byte[] LEGACY_KEY = "_venii_rebot_n1_".getBytes(StandardCharsets.UTF_8);
    public static final int MAX_PAYLOAD = 65536;
    private VeniiFrameCodec() {}

    public static byte[] encode(String json) throws GeneralSecurityException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        int paddedLength = (data.length + 15) / 16 * 16;
        byte[] padded = Arrays.copyOf(data, paddedLength);
        Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"));
        byte[] b64 = Base64.getEncoder().encode(aes.doFinal(padded));
        if (b64.length > MAX_PAYLOAD) throw new IllegalArgumentException("Message trop volumineux");
        ByteBuffer frame = ByteBuffer.allocate(4 + b64.length).order(ByteOrder.BIG_ENDIAN);
        frame.putInt(b64.length);
        frame.put(b64);
        return frame.array();
    }

    public static String decodeFrame(InputStream source) throws IOException, GeneralSecurityException {
        int size = ByteBuffer.wrap(readFully(source, 4)).order(ByteOrder.BIG_ENDIAN).getInt();
        if (size <= 0 || size > MAX_PAYLOAD) throw new IOException("Taille de réponse inattendue");
        byte[] b64 = readFully(source, size);
        byte[] ciphertext;
        try { ciphertext = Base64.getDecoder().decode(b64); }
        catch (IllegalArgumentException exc) { throw new IOException("Réponse Base64 incorrecte"); }
        if (ciphertext.length == 0 || ciphertext.length % 16 != 0)
            throw new IOException("Réponse AES incorrecte");
        Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"));
        byte[] plain = aes.doFinal(ciphertext);
        int end = plain.length;
        while (end > 0 && plain[end - 1] == 0) end--;
        return new String(plain, 0, end, StandardCharsets.UTF_8);
    }

    public static byte[] readFully(InputStream in, int bytes) throws IOException {
        byte[] out = new byte[bytes];
        int pos = 0;
        while (pos < bytes) {
            int count = in.read(out, pos, bytes - pos);
            if (count < 0) throw new EOFException("Connexion interrompue");
            if (count == 0) continue;
            pos += count;
        }
        return out;
    }
}
