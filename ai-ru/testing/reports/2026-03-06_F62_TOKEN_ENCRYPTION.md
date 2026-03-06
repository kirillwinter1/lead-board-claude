# QA Report: F62 OAuth Token Encryption
**Date:** 2026-03-06
**Tester:** Claude QA Agent

## Summary
- Overall status: **PASS WITH ISSUES**
- Unit tests: 9 passed, 0 failed
- Regression: 0 new failures (50 pre-existing in WorkflowConfigController, BoardController, TeamController)
- API tests: Backend not restarted with new code (live API testing N/A)
- Visual: N/A (backend-only feature)

## Test Coverage Review

### EncryptionServiceTest — 9 tests
| Test | Coverage | Verdict |
|------|----------|---------|
| encryptDecryptRoundtrip | Happy path | PASS |
| encryptProducesDifferentCiphertextEachTime | Random IV verification | PASS |
| nullHandling | Null safety | PASS |
| emptyStringHandling | Edge case | PASS |
| decryptSafeFallsBackToPlaintext | Migration compatibility | PASS |
| decryptSafeWorksWithEncryptedValues | Normal decrypt via safe path | PASS |
| isLikelyEncryptedDetection | Heuristic accuracy | PASS |
| noOpModeWhenKeyIsBlank | Backward compat (blank key) | PASS |
| noOpModeWhenKeyIsNull | Backward compat (null key) | PASS |

### Test Coverage Gaps

1. **No tests for EncryptedStringConverter** — the JPA converter is not unit-tested separately. Since it delegates to EncryptionService which is well-tested, risk is low, but a standalone test would improve confidence.
2. **No tests for TokenEncryptionMigrationService** — the JDBC migration logic is untested. Would need an embedded DB (H2) or Testcontainers to test properly.
3. **No integration test** verifying end-to-end: save entity via JPA → check DB has encrypted value → load entity → get plaintext back.
4. **No test for `isLikelyEncrypted` with a real Jira API token** (e.g., `ATATT3xFfGF0...`) to ensure it's not falsely detected as encrypted.

## Bugs Found

### Medium
1. **Hardcoded salt `"deadbeef"`** (EncryptionService.java:28) — The PBKDF2 salt is static and short (4 bytes). If attacker gets DB + source code, key derivation is weakened. Not blocking, but a security concern.
   - Recommendation: Use a longer hex salt (16 bytes minimum), ideally configurable.

2. **Regex recompilation per call** (EncryptionService.java:75) — `value.matches("^[0-9a-f]+$")` compiles a new Pattern on each invocation. Called on every JPA entity read for encrypted fields.
   - Recommendation: `private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-f]+$");` + `HEX_PATTERN.matcher(value).matches()`.

### Low
3. **Double `sanitizeSchema()` call** (TokenEncryptionMigrationService.java:108,119) — Same schema sanitized twice per tenant (SELECT + UPDATE).
   - Recommendation: Sanitize once, store result.

4. **`jira_api_token` column length = 500** (TenantJiraConfigEntity.java:37) — After encryption, a 200-char token becomes ~448 hex chars, close to the 500 limit. Jira API tokens are ~24 chars (safe), but future edge cases possible.
   - Recommendation: Consider widening column to TEXT in a future migration, or document constraint.

5. **Static volatile singleton pattern** (EncryptionService.java:14,33) — Works correctly but is an unusual pattern for Spring. The static instance is needed because JPA `@Converter` classes are instantiated by Hibernate, not Spring. Documented by design.

## Business Logic Verification

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| No TOKEN_ENCRYPTION_KEY | noOp mode, tokens passthrough | Verified in test | PASS |
| With TOKEN_ENCRYPTION_KEY | AES-256 encryption | Verified in test | PASS |
| Encrypt same value twice | Different ciphertext (random IV) | Verified in test | PASS |
| Decrypt plaintext via decryptSafe | Returns plaintext as-is | Verified in test | PASS |
| Null token values | Returns null, no NPE | Verified in test | PASS |
| Migration idempotency | Already-encrypted tokens skipped | Logic verified in code review | PASS |
| SQL injection in schema names | Blocked by sanitizeSchema() | Logic verified in code review | PASS |

## Security Review

| Check | Status | Notes |
|-------|--------|-------|
| AES-256 algorithm | PASS | Spring Security Crypto Encryptors.text() |
| Random IV per encryption | PASS | Each ciphertext unique |
| No plaintext token in logs | PASS | Only counts logged, never values |
| SQL injection in migration | PASS | sanitizeSchema() whitelist |
| Key not logged or exposed | PASS | Only "enabled/disabled" message |
| Backward compatibility | PASS | noOp mode without key |
| Salt strength | CONCERN | Hardcoded "deadbeef" — see Bug #1 |

## Documentation Review

| Document | Updated | Status |
|----------|---------|--------|
| F62_TOKEN_ENCRYPTION.md | Created | PASS |
| FEATURES.md | F62 added | PASS |
| TECH_DEBT.md | #1 CRITICAL resolved | PASS |
| DEPLOY.md | TOKEN_ENCRYPTION_KEY documented | PASS |
| Version bumped to 0.62.0 | backend + frontend | PASS |

## Recommendations

1. **Add compiled Pattern constant** — Easy fix, improves performance
2. **Strengthen salt** — Change from "deadbeef" to at least 16-byte hex
3. **Add integration test** — JPA roundtrip test with H2 verifying encrypted storage
4. **Add TokenEncryptionMigrationService test** — With embedded DB
5. **Live testing** — Restart backend with TOKEN_ENCRYPTION_KEY and verify tokens are encrypted in DB, Jira sync still works
