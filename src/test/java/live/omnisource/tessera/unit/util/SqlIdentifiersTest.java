package live.omnisource.tessera.unit.util;

import live.omnisource.tessera.util.SqlIdentifiers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class SqlIdentifiersTest {

    @ParameterizedTest
    @ValueSource(strings = {"users", "my_table", "Column1", "_hidden", "a"})
    void validate_acceptsSafeIdentifiers(String id) {
        assertThat(SqlIdentifiers.validate(id, "test")).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123start", "drop;table", "my table", "a\"b", "x'y", "has.dot"})
    void validate_rejectsUnsafeIdentifiers(String id) {
        assertThatThrownBy(() -> SqlIdentifiers.validate(id, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejectsNull() {
        assertThatThrownBy(() -> SqlIdentifiers.validate(null, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_rejectsOverlyLongIdentifier() {
        String longId = "a".repeat(129);
        assertThatThrownBy(() -> SqlIdentifiers.validate(longId, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quoteDouble_wrapsInDoubleQuotes() {
        assertThat(SqlIdentifiers.quoteDouble("my_table", "test")).isEqualTo("\"my_table\"");
    }

    @Test
    void quoteBacktick_wrapsInBackticks() {
        assertThat(SqlIdentifiers.quoteBacktick("my_table", "test")).isEqualTo("`my_table`");
    }

    @Test
    void isSafe_returnsTrueForValid() {
        assertThat(SqlIdentifiers.isSafe("valid_name")).isTrue();
    }

    @Test
    void isSafe_returnsFalseForInvalid() {
        assertThat(SqlIdentifiers.isSafe("has space")).isFalse();
        assertThat(SqlIdentifiers.isSafe(null)).isFalse();
    }
}