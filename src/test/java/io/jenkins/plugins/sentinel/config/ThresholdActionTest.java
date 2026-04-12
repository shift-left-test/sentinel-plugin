package io.jenkins.plugins.sentinel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ThresholdActionTest {

    @Test
    void fromStringReturnsFailure() {
        assertThat(ThresholdAction.fromString("FAILURE"))
                .isEqualTo(ThresholdAction.FAILURE);
    }

    @Test
    void fromStringReturnsUnstable() {
        assertThat(ThresholdAction.fromString("UNSTABLE"))
                .isEqualTo(ThresholdAction.UNSTABLE);
    }

    @Test
    void fromStringIsCaseInsensitiveLowercase() {
        assertThat(ThresholdAction.fromString("failure"))
                .isEqualTo(ThresholdAction.FAILURE);
    }

    @Test
    void fromStringIsCaseInsensitiveMixed() {
        assertThat(ThresholdAction.fromString("Unstable"))
                .isEqualTo(ThresholdAction.UNSTABLE);
    }

    @Test
    void fromStringThrowsOnInvalidValue() {
        assertThatThrownBy(() -> ThresholdAction.fromString("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID");
    }

    @Test
    void fromStringThrowsOnNull() {
        assertThatThrownBy(() -> ThresholdAction.fromString(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failureToStringReturnsName() {
        assertThat(ThresholdAction.FAILURE.toString())
                .isEqualTo("FAILURE");
    }

    @Test
    void unstableToStringReturnsName() {
        assertThat(ThresholdAction.UNSTABLE.toString())
                .isEqualTo("UNSTABLE");
    }
}
