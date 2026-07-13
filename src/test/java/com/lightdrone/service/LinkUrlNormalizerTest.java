package com.lightdrone.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinkUrlNormalizerTest {

    private final LinkUrlNormalizer normalizer = new LinkUrlNormalizer();

    @Test
    void normalizesInternalRelativePath() {
        assertThat(normalizer.normalize("products/51")).isEqualTo("/products/51");
    }

    @Test
    void keepsSafeAbsoluteAndRootRelativeUrls() {
        assertThat(normalizer.normalize("/products/51")).isEqualTo("/products/51");
        assertThat(normalizer.normalize("https://lightdrone.co.kr/products/51"))
                .isEqualTo("https://lightdrone.co.kr/products/51");
        assertThat(normalizer.normalize("tel:010-3565-9741")).isEqualTo("tel:010-3565-9741");
    }

    @Test
    void dropsUnsafeUrls() {
        assertThat(normalizer.normalize("javascript:alert(1)")).isNull();
        assertThat(normalizer.normalize("data:text/html,boom")).isNull();
        assertThat(normalizer.normalize("//example.com/path")).isNull();
        assertThat(normalizer.normalize(" ")).isNull();
    }
}
