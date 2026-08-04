package com.dorosoft.erp.identity.application.account;

import com.dorosoft.erp.identity.application.error.IdentityErrorCode;
import com.dorosoft.erp.identity.application.error.IdentityException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IfMatchVersion {
    private static final Pattern FORMAT = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");

    private IfMatchVersion() {
    }

    public static long parse(String header) {
        if (header == null || header.isBlank()) {
            throw new IdentityException(IdentityErrorCode.PRECONDITION_REQUIRED);
        }
        Matcher matcher = FORMAT.matcher(header);
        if (!matcher.matches()) {
            throw new IdentityException(IdentityErrorCode.PRECONDITION_FAILED);
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IdentityException(IdentityErrorCode.PRECONDITION_FAILED);
        }
    }

    public static void verify(long expected, long actual) {
        if (expected != actual) {
            throw new IdentityException(IdentityErrorCode.PRECONDITION_FAILED);
        }
    }
}
