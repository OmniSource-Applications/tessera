package live.omnisource.tessera.filestore.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SecretRefTest {

    @Test
    void parse_roundTrips_toString() {
        var ref = SecretRef.parse("db/default/123/passwd");
        assertThat(ref.key()).isEqualTo("db/default/123/passwd");
        assertThat(ref.toString()).contains("db/default/123/passwd");
    }

    @Test
    void parse_rejectsBlank() {
        assertThatThrownBy(() -> SecretRef.parse(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
