package com.dorosoft.erp.catalog.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MediaContentType 허용 형식(ADR-007)")
class MediaContentTypeTest {

    @Test
    @DisplayName("JPEG·PNG·WebP는 허용하고 각 확장자를 매핑한다")
    void allowsThreeFormats() {
        assertThat(MediaContentType.fromMimeType("image/jpeg")).contains(MediaContentType.JPEG);
        assertThat(MediaContentType.JPEG.extension()).isEqualTo("jpg");
        assertThat(MediaContentType.fromMimeType("image/png")).contains(MediaContentType.PNG);
        assertThat(MediaContentType.PNG.extension()).isEqualTo("png");
        assertThat(MediaContentType.fromMimeType("image/webp")).contains(MediaContentType.WEBP);
        assertThat(MediaContentType.WEBP.extension()).isEqualTo("webp");
    }

    @Test
    @DisplayName("대소문자와 무관하게 매칭한다")
    void isCaseInsensitive() {
        assertThat(MediaContentType.fromMimeType("IMAGE/WEBP")).contains(MediaContentType.WEBP);
    }

    @Test
    @DisplayName("SVG·HTML·실행 형식은 거부한다")
    void rejectsDisallowedFormats() {
        assertThat(MediaContentType.fromMimeType("image/svg+xml")).isEmpty();
        assertThat(MediaContentType.fromMimeType("text/html")).isEmpty();
        assertThat(MediaContentType.fromMimeType("application/x-msdownload")).isEmpty();
        assertThat(MediaContentType.isAllowed("image/svg+xml")).isFalse();
    }

    @Test
    @DisplayName("빈 값이나 알 수 없는 값은 Optional.empty를 반환한다")
    void returnsEmptyForUnknown() {
        Optional<MediaContentType> result = MediaContentType.fromMimeType("application/octet-stream");
        assertThat(result).isEmpty();
    }
}
