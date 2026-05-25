package se.liaprojekt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.liaprojekt.exception.BadRequestException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues and validates short-lived signed stream tokens.
 *
 * <p>A token encodes the {@code fileId} and an expiry epoch-second, signed with
 * HMAC-SHA256 using a server-side secret. It is designed to be passed as a URL
 * query parameter so the browser's {@code <video src="...">} element can reach
 * the stream endpoint without needing an {@code Authorization} header.
 *
 * <p>Token format (before Base64URL encoding):
 * <pre>{@code <fileId>:<expiryEpochSeconds>:<hmacHex>}</pre>
 *
 * <p>The HMAC is computed over {@code "<fileId>:<expiryEpochSeconds>"}, binding
 * the signature to both the specific file and its expiry so neither can be tampered
 * with independently.
 */
@Service
public class StreamTokenService {

    private static final Logger log = LoggerFactory.getLogger(StreamTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secretKey;
    private final long tokenTtlSeconds;

    /**
     * @param secret         HMAC secret injected from {@code stream.token.secret}.
     *                       Must be at least 32 characters for adequate security.
     * @param tokenTtlSeconds Lifetime of issued tokens in seconds, from
     *                       {@code stream.token.ttl-seconds} (default: 300 = 5 minutes).
     */
    public StreamTokenService(
            @Value("${stream.token.secret}") String secret,
            @Value("${stream.token.ttl-seconds:300}") long tokenTtlSeconds) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "stream.token.secret must be at least 32 characters long.");
        }
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the configured token lifetime in seconds.
     * Exposed so callers (e.g. the controller) can include {@code expiresIn}
     * in their response, allowing the frontend to schedule a proactive refresh.
     *
     * @return Token TTL in seconds
     */
    public long getTtlSeconds() {
        return tokenTtlSeconds;
    }

    /**
     * Issues a signed token that authorises streaming of the given {@code fileId}.
     *
     * <p>The token is valid for {@code stream.token.ttl-seconds} seconds from now
     * (default 5 minutes) — long enough for the browser to start playback, short
     * enough to limit the usefulness of a leaked URL.
     *
     * @param fileId The opaque file identifier the token is scoped to
     * @return A Base64URL-encoded token string safe to embed directly in a URL
     */
    public String issue(String fileId) {
        long expiry = (System.currentTimeMillis() / 1000) + tokenTtlSeconds;
        String payload = fileId + ":" + expiry;
        String hmac = sign(payload);
        // Raw token: "<fileId>:<expiry>:<hmac>"
        String raw = payload + ":" + hmac;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates a stream token and returns the authorised {@code fileId}.
     *
     * <p>Validation checks, in order:
     * <ol>
     *   <li>The token can be Base64URL-decoded and split into exactly three parts.</li>
     *   <li>The HMAC over {@code "<fileId>:<expiry>"} matches the embedded signature.</li>
     *   <li>The expiry epoch-second is in the future.</li>
     * </ol>
     *
     * @param token The Base64URL-encoded token from the request query parameter
     * @param fileId The {@code fileId} from the URL path — must match the token's embedded ID
     * @throws BadRequestException if the token is missing, malformed, expired, or for a different file
     */
    public void validate(String token, String fileId) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Missing stream token.");
        }

        String raw;
        try {
            raw = new String(
                    Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Malformed stream token.");
        }

        // Expected format: "<fileId>:<expiry>:<hmac>"
        // fileId itself is a UUID (no colons), so split into exactly 3 parts is unambiguous
        String[] parts = raw.split(":", 3);
        if (parts.length != 3) {
            throw new BadRequestException("Malformed stream token.");
        }

        String tokenFileId = parts[0];
        String expiryStr   = parts[1];
        String tokenHmac   = parts[2];

        // 1. Verify the token is for the requested file
        if (!tokenFileId.equals(fileId)) {
            log.warn("Stream token fileId mismatch: token='{}', request='{}'", tokenFileId, fileId);
            throw new BadRequestException("Stream token is not valid for this file.");
        }

        // 2. Verify HMAC — do this before checking expiry to avoid timing leaks on forged tokens
        String expectedHmac = sign(tokenFileId + ":" + expiryStr);
        if (!constantTimeEquals(expectedHmac, tokenHmac)) {
            log.warn("Stream token HMAC verification failed for fileId '{}'", fileId);
            throw new BadRequestException("Stream token signature is invalid.");
        }

        // 3. Check expiry
        long expiry;
        try {
            expiry = Long.parseLong(expiryStr);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Malformed stream token.");
        }

        if ((System.currentTimeMillis() / 1000) > expiry) {
            throw new BadRequestException("Stream token has expired.");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes HMAC-SHA256 over {@code payload} using the configured secret key.
     *
     * @param payload String to sign
     * @return Lowercase hex-encoded HMAC digest
     */
    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // HmacSHA256 is guaranteed by the JDK spec; this should never happen
            throw new IllegalStateException("HMAC-SHA256 unavailable", ex);
        }
    }

    /**
     * Constant-time string equality check to prevent timing attacks.
     * Returns true only if both strings are identical in length and content.
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}