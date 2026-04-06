package live.omnisource.tessera.unit.crypto;

import live.omnisource.tessera.filestore.crypto.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HexTest {

    @Test
    void toHex_encodesEmptyArray() {
        assertThat(Hex.toHex(new byte[0])).isEmpty();
    }

    @Test
    void toHex_encodesSingleByte() {
        assertThat(Hex.toHex(new byte[]{(byte) 0xFF})).isEqualTo("ff");
        assertThat(Hex.toHex(new byte[]{0x00})).isEqualTo("00");
        assertThat(Hex.toHex(new byte[]{0x0A})).isEqualTo("0a");
    }

    @Test
    void toHex_encodesMultipleBytes() {
        byte[] input = {0x48, 0x33, 0x47, 0x54}; // H3GT magic bytes
        assertThat(Hex.toHex(input)).isEqualTo("48334754");
    }

    @Test
    void toHex_outputIsAlwaysLowerCase() {
        byte[] input = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
        String hex = Hex.toHex(input);
        assertThat(hex).isEqualTo("abcdef");
        assertThat(hex).isEqualTo(hex.toLowerCase());
    }

    @Test
    void toHex_outputLengthIsDoubleInputLength() {
        byte[] input = new byte[32];
        assertThat(Hex.toHex(input)).hasSize(64);
    }
}