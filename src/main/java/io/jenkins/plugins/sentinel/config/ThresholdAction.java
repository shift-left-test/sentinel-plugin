package io.jenkins.plugins.sentinel.config;

/**
 * Action to take when mutation score falls below threshold.
 */
public enum ThresholdAction {

    FAILURE,
    UNSTABLE;

    /**
     * Parses a string into a ThresholdAction (case-insensitive).
     *
     * @param value the string to parse
     * @return the corresponding ThresholdAction
     * @throws IllegalArgumentException if value is null or not a valid action
     */
    public static ThresholdAction fromString(final String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "ThresholdAction value must not be null. "
                            + "Valid values: FAILURE, UNSTABLE");
        }
        try {
            return valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid ThresholdAction: '" + value
                            + "'. Valid values: FAILURE, UNSTABLE",
                    e);
        }
    }
}
