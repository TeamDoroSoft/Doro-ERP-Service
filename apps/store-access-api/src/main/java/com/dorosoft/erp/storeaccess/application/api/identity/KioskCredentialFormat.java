package com.dorosoft.erp.storeaccess.application.api.identity;

import java.util.Optional;

/**
 * The single source of truth for the {@code kdc_{credentialId}.{secret}} Kiosk credential wire format
 * (ADR-02-013), shared by activation (formats the Cookie value) and the per-request Cookie authentication
 * Filter (parses it). {@code credentialId}/{@code secret} are always Base64URL without padding, so they never
 * contain a {@code .}, making a first-{@code .} split unambiguous.
 */
public final class KioskCredentialFormat {

    private static final String PREFIX = "kdc_";

    private KioskCredentialFormat() {
    }

    public static String format(String credentialId, String secret) {
        return PREFIX + credentialId + "." + secret;
    }

    public static Optional<ParsedKioskCredential> parse(String fullCredential) {
        if (fullCredential == null || !fullCredential.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String remainder = fullCredential.substring(PREFIX.length());
        int dotIndex = remainder.indexOf('.');
        if (dotIndex <= 0 || dotIndex == remainder.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new ParsedKioskCredential(remainder.substring(0, dotIndex), remainder.substring(dotIndex + 1)));
    }

    public record ParsedKioskCredential(String credentialId, String secret) {
    }
}
