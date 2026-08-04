package com.dorosoft.erp.identity.application.port;

import com.dorosoft.erp.identity.domain.credential.PasswordPolicy;

/** 평문을 저장하지 않는 비밀번호 Hash 경계. */
public interface PasswordHasher extends PasswordPolicy.PasswordHashMatcher {
    String hash(CharSequence normalizedPassword);

    boolean needsRehash(String encodedHash);
}
