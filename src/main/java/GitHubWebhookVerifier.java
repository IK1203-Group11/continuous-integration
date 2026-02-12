import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility for verifying GitHub webhook deliveries using HMAC SHA-256.
 *
 * GitHub sends the signature in header: X-Hub-Signature-256: sha256=<hex>
 * We recompute HMAC(secret, payload) and compare using a timing-safe method.
 */
public final class GitHubWebhookVerifier {

    private GitHubWebhookVerifier() { }

    /**
     * Verifies a GitHub webhook payload against the X-Hub-Signature-256 header.
     *
     * @param payload     raw request body (exact bytes/characters used to compute signature)
     * @param headerSig   header value, expected format "sha256=<hex>"
     * @param secret      webhook secret configured both in GitHub and on the server
     * @return true if signature matches, false otherwise
     */
    public static boolean verify(String payload, String headerSig, String secret) {
        if (secret == null || secret.isBlank()) return false;
        if (payload == null) payload = "";
        if (headerSig == null || !headerSig.startsWith("sha256=")) return false;

        String expected = "sha256=" + hmacSha256Hex(secret, payload);

        // Timing-safe comparison
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                headerSig.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Computes hex(HMAC_SHA256(secret, payload)).
     * Package-private for unit tests.
     */
    static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);

            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Should never happen in standard JDK; wrap to keep signature clean.
            throw new RuntimeException("Failed to compute HMAC SHA-256", e);
        }
    }
}
