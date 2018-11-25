package com.brimma.bpm.util;

/**
 * This will just transform the Encompass disclosure type to Home Bridge Disclosure type
 */
public class DisclosureUtil {
    public static String getDisclosureForBSS(String disclosureType) {
        return "Initial".equalsIgnoreCase(disclosureType) ? "INITDISCLS" : (
                ("Revised".equalsIgnoreCase(disclosureType) || "final".equalsIgnoreCase(disclosureType)) ? "REDISCLS" : disclosureType
        );
    }
}
