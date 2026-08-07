package com.dorosoft.erp.storeaccess.application.api.identity;

import com.dorosoft.erp.platform.web.ProblemAwareException;
import com.dorosoft.erp.storeaccess.domain.identity.LoginId;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Employee password acceptability policy (ADR-02-009): length, a static common/leaked-password blocklist,
 * rejection of passwords derived from the service name or the account's own {@code loginId}, and rejection
 * of reusing the current password. Complexity rules (uppercase/digit/symbol composition) are intentionally
 * not enforced.
 */
@Component
public class PasswordPolicyValidator {

    private static final int MIN_LENGTH = 15;
    private static final int MAX_LENGTH = 128;
    private static final List<String> SERVICE_DERIVED_TERMS = List.of("doro", "storeaccess", "doroerp");
    private static final String BLOCKLIST_RESOURCE = "security/password-blocklist.txt";

    private final Set<String> blocklist;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicyValidator(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.blocklist = loadBlocklist();
    }

    /**
     * Validates a candidate password. {@code currentPasswordHash} may be {@code null} when there is no
     * existing password to compare against (new employee creation).
     *
     * <p>For idempotency-wrapped operations (employee creation, admin password reset), call
     * {@link #validateFormat} before claiming the Idempotency-Key and {@link #validateNotReusingCurrent}
     * only inside the guarded operation itself — reuse-checking reads the account's current password hash,
     * which the operation's own first successful run changes, so checking it before the claim would reject
     * a legitimate replay of an already-completed request.
     *
     * <p>{@code candidatePassword} must already be {@link #normalize(String) normalized}: callers normalize
     * once at the entry point and use that same value for every subsequent validation, Hash and comparison,
     * so validation never runs against a different string than what is ultimately persisted (ADR-02-009).
     */
    public void validate(String candidatePassword, LoginId loginId, String currentPasswordHash) {
        validateFormat(candidatePassword, loginId);
        if (currentPasswordHash != null) {
            validateNotReusingCurrent(candidatePassword, currentPasswordHash);
        }
    }

    /** NFC-normalizes a raw password; callers use the returned value for validation, Hash and comparison alike. */
    public static String normalize(String rawPassword) {
        return Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
    }

    /** The stateless subset of the policy: length, blocklist membership, and derived-term rejection. */
    public void validateFormat(String candidatePassword, LoginId loginId) {
        String normalized = normalize(candidatePassword);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw weakPassword();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (blocklist.contains(lower)) {
            throw weakPassword();
        }
        if (loginId != null && lower.contains(loginId.value())) {
            throw weakPassword();
        }
        if (SERVICE_DERIVED_TERMS.stream().anyMatch(lower::contains)) {
            throw weakPassword();
        }
    }

    public void validateNotReusingCurrent(String candidatePassword, String currentPasswordHash) {
        if (passwordEncoder.matches(candidatePassword, currentPasswordHash)) {
            throw new ProblemAwareException(
                    PasswordManagementProblemCode.PASSWORD_REUSE_NOT_ALLOWED,
                    "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
        }
    }

    private ProblemAwareException weakPassword() {
        return new ProblemAwareException(
                PasswordManagementProblemCode.WEAK_PASSWORD, "비밀번호가 정책을 만족하지 않습니다.");
    }

    private Set<String> loadBlocklist() {
        Set<String> entries = new HashSet<>();
        try (InputStream inputStream = new ClassPathResource(BLOCKLIST_RESOURCE).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                entries.add(trimmed.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load password blocklist", e);
        }
        return Set.copyOf(entries);
    }
}
