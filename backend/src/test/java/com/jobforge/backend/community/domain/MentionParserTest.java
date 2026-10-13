package com.jobforge.backend.community.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** NOT RUN in the authoring session (no builds were authorized). */
class MentionParserTest {

    @Test
    void extractsDistinctLowerCasedHandlesInOrder() {
        assertThat(MentionParser.handles("Hi @Asha_K and @bob99, cc @asha_k!")).containsExactly("asha_k", "bob99");
    }

    @Test
    void ignoresEmailAddressesShortHandlesAndEmbeddedAts() {
        assertThat(MentionParser.handles("mail me at user@example.com")).isEmpty();
        assertThat(MentionParser.handles("@ab is too short")).isEmpty();
        assertThat(MentionParser.handles("@@double")).isEmpty();
    }

    @Test
    void handlesNullAndCapsTheNumberOfMentions() {
        assertThat(MentionParser.handles(null)).isEmpty();
        String text = IntStream.range(0, 30).mapToObj(i -> "@user_" + (100 + i)).collect(Collectors.joining(" "));
        assertThat(MentionParser.handles(text)).hasSize(MentionParser.MAX_MENTIONS);
    }
}
