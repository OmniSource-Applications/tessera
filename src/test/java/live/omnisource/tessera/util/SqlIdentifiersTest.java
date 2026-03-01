package live.omnisource.tessera.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SqlIdentifiersTest {

    @Test
    void quoteIdent_wrapsAndEscapesQuotes() {
        assertThat(SqlIdentifiers.quoteIdent("abc")).isEqualTo("\"abc\"");
        assertThat(SqlIdentifiers.quoteIdent("a\"b")).isEqualTo("\"a\"\"b\"");
    }

    @Test
    void quoteIdent_rejectsBlank() {
        assertThatThrownBy(() -> SqlIdentifiers.quoteIdent(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
