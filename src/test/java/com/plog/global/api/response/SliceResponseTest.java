package com.plog.global.api.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

class SliceResponseTest {

    @Test
    void mapsOnlyInfiniteScrollMetadata() {
        SliceResponse<String> response = SliceResponse.of(
                new SliceImpl<>(List.of("source"), PageRequest.of(2, 10), true),
                List.of("result")
        );

        assertThat(response.content()).containsExactly("result");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.hasNext()).isTrue();
    }
}
