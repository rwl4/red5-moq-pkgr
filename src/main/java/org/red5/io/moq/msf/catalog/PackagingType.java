package org.red5.io.moq.msf.catalog;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MSF packaging types per draft-ietf-moq-msf section 5.1.12.
 */
public enum PackagingType {
    /** LOC packaged media content */
    LOC("loc"),

    /** CMAF packaged media content via CMSF */
    CMAF("cmaf"),

    /** Media timeline track (section 7) */
    MEDIA_TIMELINE("mediatimeline"),

    /** Event timeline track (section 8) */
    EVENT_TIMELINE("eventtimeline");

    private final String value;

    PackagingType(String value) {
        this.value = value;
    }

    /**
     * Get the string value used in JSON catalog.
     */
    public String getValue() {
        return value;
    }

    /**
     * Get all packaging type values as a set.
     */
    public static Set<String> allValues() {
        return Arrays.stream(values())
                .map(PackagingType::getValue)
                .collect(Collectors.toSet());
    }

    /**
     * Check if a packaging value is valid for MSF.
     */
    public static boolean isValid(String packaging) {
        return allValues().contains(packaging);
    }

    /**
     * Parse a string value to PackagingType enum.
     * @return the matching PackagingType or null if invalid
     */
    public static PackagingType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PackagingType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
