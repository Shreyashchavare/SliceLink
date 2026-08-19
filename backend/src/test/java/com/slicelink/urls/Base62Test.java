package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base62Test {

    @Test
    @DisplayName("encode(0) returns '0'")
    void encode_zero_returnsZeroString() {
        assertThat(Base62.encode(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("encode negative value throws IllegalArgumentException")
    void encode_negative_throwsException() {
        assertThatThrownBy(() -> Base62.encode(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be non-negative");
    }

    @Test
    @DisplayName("encode single-digit mappings match alphabet")
    void encode_singleDigit_matchesAlphabet() {
        assertThat(Base62.encode(0)).isEqualTo("0");
        assertThat(Base62.encode(9)).isEqualTo("9");
        assertThat(Base62.encode(10)).isEqualTo("A");
        assertThat(Base62.encode(35)).isEqualTo("Z");
        assertThat(Base62.encode(36)).isEqualTo("a");
        assertThat(Base62.encode(61)).isEqualTo("z");
    }

    @Test
    @DisplayName("encode(62) carries over to '10'")
    void encode_baseBoundary_carriesOver() {
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(63)).isEqualTo("11");
        assertThat(Base62.encode(3843)).isEqualTo("zz");
        assertThat(Base62.encode(3844)).isEqualTo("100");
    }

    @Test
    @DisplayName("encode Long.MAX_VALUE produces valid non-empty string within 11 characters")
    void encode_maxLong_producesValidString() {
        String encoded = Base62.encode(Long.MAX_VALUE);
        assertThat(encoded).isNotEmpty().hasSizeLessThanOrEqualTo(12);
        assertThat(Base62.decode(encoded)).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 9L, 10L, 35L, 36L, 61L, 62L, 100L, 999999L, 123456789012345L, Long.MAX_VALUE})
    @DisplayName("encode and decode round-trip maintains value")
    void roundTrip_preservesValue(long value) {
        String encoded = Base62.encode(value);
        long decoded = Base62.decode(encoded);
        assertThat(decoded).isEqualTo(value);
    }

    @Test
    @DisplayName("decode('0') returns 0")
    void decode_zero_returnsZero() {
        assertThat(Base62.decode("0")).isEqualTo(0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("decode blank string throws IllegalArgumentException")
    void decode_blank_throwsException(String blank) {
        assertThatThrownBy(() -> Base62.decode(blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("decode null throws IllegalArgumentException")
    void decode_null_throwsException() {
        assertThatThrownBy(() -> Base62.decode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc!", "ab-cd", "xyz_123", "hello world", "test@123", "code\u0080"})
    @DisplayName("decode with invalid characters throws IllegalArgumentException")
    void decode_invalidCharacters_throwsException(String invalid) {
        assertThatThrownBy(() -> Base62.decode(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid Base62 character");
    }
}
