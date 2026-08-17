package com.slicelink.urls;

/**
 * Stateless Base62 encoder/decoder.
 *
 * <p>Alphabet (62 characters, index 0–61):
 * <pre>
 *   0–9   → digits      '0'..'9'
 *  10–35  → uppercase   'A'..'Z'
 *  36–61  → lowercase   'a'..'z'
 * </pre>
 *
 * <p>This ordering keeps short codes URL-safe, avoids look-alike characters
 * being ambiguous only in visual rendering (all chars are distinct), and
 * produces codes that sort in the same order as their numeric source when
 *  left-padded to equal length.
 *
 * <p>No external dependencies. No business logic. Thread-safe (stateless).
 */
public final class Base62 {

    /** The 62-character encoding alphabet. */
    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int BASE = 62;

    // Reverse-lookup table: ASCII code → digit value, -1 if not in alphabet.
    private static final int[] DECODE_TABLE = new int[128];

    static {
        java.util.Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE_TABLE[ALPHABET.charAt(i)] = i;
        }
    }

    private Base62() { }   // utility class

    /**
     * Encodes a non-negative {@code long} to its Base62 representation.
     *
     * @param value a non-negative number
     * @return Base62 string, never blank; {@code "0"} for input {@code 0}
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative, got: " + value);
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to a {@code long}.
     *
     * @param encoded a non-empty string using only characters from {@link #ALPHABET}
     * @return the decoded non-negative {@code long}
     * @throws IllegalArgumentException if the string is blank or contains
     *                                  characters outside the alphabet
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("encoded string must not be blank");
        }
        long result = 0;
        for (char c : encoded.toCharArray()) {
            int digit = (c < 128) ? DECODE_TABLE[c] : -1;
            if (digit < 0) {
                throw new IllegalArgumentException(
                        "invalid Base62 character '" + c + "' in: " + encoded);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
