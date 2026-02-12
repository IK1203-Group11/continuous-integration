import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Unit tests for GitHubWebhookVerifier.
 *
 * These tests do not perform any network calls. They only verify that:
 *  - valid signatures are accepted
 *  - invalid or missing signatures are rejected
 *  - missing/blank secret is rejected
 */
public class GitHubWebhookVerifierTest {

    @Test
    public void verify_validSignature_returnsTrue() {
        String secret = "super-secret";
        String payload = "{\"hello\":\"world\"}";

        String hex = GitHubWebhookVerifier.hmacSha256Hex(secret, payload);
        String header = "sha256=" + hex;

        assertTrue(GitHubWebhookVerifier.verify(payload, header, secret));
    }

    @Test
    public void verify_wrongSignature_returnsFalse() {
        String secret = "super-secret";
        String payload = "{\"hello\":\"world\"}";

        // Same format, but signature does not match payload+secret
        String header = "sha256=" + "0".repeat(64);

        assertFalse(GitHubWebhookVerifier.verify(payload, header, secret));
    }

    @Test
    public void verify_missingHeader_returnsFalse() {
        String secret = "super-secret";
        String payload = "{\"hello\":\"world\"}";

        assertFalse(GitHubWebhookVerifier.verify(payload, null, secret));
    }

    @Test
    public void verify_wrongPrefix_returnsFalse() {
        String secret = "super-secret";
        String payload = "{\"hello\":\"world\"}";

        String hex = GitHubWebhookVerifier.hmacSha256Hex(secret, payload);
        String header = "sha1=" + hex; // wrong prefix

        assertFalse(GitHubWebhookVerifier.verify(payload, header, secret));
    }

    @Test
    public void verify_blankSecret_returnsFalse() {
        String payload = "{\"hello\":\"world\"}";
        String header = "sha256=" + "0".repeat(64);

        assertFalse(GitHubWebhookVerifier.verify(payload, header, ""));
        assertFalse(GitHubWebhookVerifier.verify(payload, header, "   "));
        assertFalse(GitHubWebhookVerifier.verify(payload, header, null));
    }

    @Test
    public void verify_nullPayload_treatedAsEmptyString() {
        String secret = "super-secret";
        String payload = "";

        String hex = GitHubWebhookVerifier.hmacSha256Hex(secret, payload);
        String header = "sha256=" + hex;

        assertTrue(GitHubWebhookVerifier.verify(null, header, secret));
    }
}
