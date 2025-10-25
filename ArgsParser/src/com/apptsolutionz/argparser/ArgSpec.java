package com.apptsolutionz.argparser;


/**
 * Defines the specification for a single command-line argument.
 * @param flag The short flag (e.g., "-f").
 * @param longFlag The long flag (e.g., "--file").
 * @param type The expected data type of the value(s).
 * @param length The number of values expected after the flag. 
 * Use 0 for a boolean switch flag (presence means true, absence means false). 
 * Use 1 or greater for arguments that require subsequent values.
 * @param required True if this argument must be present.
 * @param defaultValue The default value if the argument is optional and not provided.
 * Can be a single value (for length 1) or a List (for length > 1).
 */
public record ArgSpec(
    String flag,
    String longFlag,
    ArgType type,
    int length,
    boolean required,
    Object defaultValue
) {
    /**
     * Compact constructor to validate argument length.
     */
    public ArgSpec {
        // --- CORRECTED: Allow length >= 0 for switch flags ---
        if (length < 0) {
            throw new IllegalArgumentException("Argument length cannot be negative.");
        }
        
        // If length is 0, type MUST be BOOLEAN
        if (length == 0 && type != ArgType.BOOLEAN) {
            throw new IllegalArgumentException("Argument with length 0 (switch flag) must have type BOOLEAN.");
        }

        // Ensure default value is compatible with the length (single value or List)
        if (defaultValue != null) {
            if (length > 1 && !(defaultValue instanceof java.util.List)) {
                throw new IllegalArgumentException("Default value for length > 1 must be a List.");
            }
        }
    }

    /**
     * Returns the primary name used in the resulting Map (the long flag if present, otherwise the short flag).
     * @return The canonical argument name.
     */
    public String getCanonicalName() {
        if (longFlag != null && !longFlag.isEmpty()) {
            // Remove the leading '--'
            return longFlag.substring(2);
        }
        // Remove the leading '-'
        return flag.substring(1);
    }
}
