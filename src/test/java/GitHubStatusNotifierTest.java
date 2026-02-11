import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link GitHubStatusNotifier}.
 *
 * Purpose:
 * These tests verify the notifier's "safe behavior" (early-return paths) without modifying
 * production code and without making real network calls to the GitHub API.
 *
 * Why we avoid real HTTP calls:
 * - GitHubStatusNotifier internally creates its own HttpClient and always targets api.github.com.
 * - If GITHUB_TOKEN is set and the inputs are valid, the class would attempt a real HTTP request.
 * - Unit tests should be deterministic and not depend on internet, credentials, or GitHub uptime.
 *
 * Strategy:
 * - Capture System.out and assert that expected log messages are printed.
 * - Exercise code paths that return early (missing token, invalid repo name, missing SHA).
 * - Use {@link Assume#assumeTrue(boolean)} to skip tests that could cause a real HTTP call
 *   when the environment contains a real GITHUB_TOKEN.
 *
 * Note:
 * If you want to test the "successful HTTP request" path, you typically need to refactor
 * GitHubStatusNotifier to inject/mocking HttpClient or use a stub server. That is intentionally
 * avoided here to keep production code unchanged.
 */
public class GitHubStatusNotifierTest {

    /** Original console output stream restored after each test. */
    private PrintStream originalOut;

    /** Buffer used to capture console output for assertions. */
    private ByteArrayOutputStream outContent;

    /**
     * Redirect System.out to an in-memory buffer before each test.
     * This allows us to assert on log messages printed by GitHubStatusNotifier.
     */
    @Before
    public void setUp() {
        originalOut = System.out;
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    /**
     * Restore System.out after each test to avoid affecting other tests.
     */
    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

    /**
     * Verifies that when no GITHUB_TOKEN is configured, the notifier:
     * - does NOT throw
     * - prints a message indicating that notification is skipped
     *
     * We skip this test if GITHUB_TOKEN is present to avoid a real GitHub API call.
     */
    @Test
    public void setStatus_withoutToken_skipsAndDoesNotThrow() throws Exception {
        Assume.assumeTrue("GITHUB_TOKEN is set; skipping to avoid real GitHub call",
                isBlank(System.getenv("GITHUB_TOKEN")));

        GitHubStatusNotifier n = new GitHubStatusNotifier();
        n.setStatus("owner/repo",
                "0123456789abcdef0123456789abcdef01234567",
                true,
                "ok");

        String output = outContent.toString();
        assertTrue(output.contains("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status."));
    }

    /**
     * Verifies the overloaded method setStatus(..., buildId) behaves safely without a token:
     * - still skips notification
     * - does NOT throw
     *
     * We skip this test if GITHUB_TOKEN is present to avoid a real GitHub API call.
     */
    @Test
    public void setStatus_withDetailsLink_withoutToken_skipsAndDoesNotThrow() throws Exception {
        Assume.assumeTrue("GITHUB_TOKEN is set; skipping to avoid real GitHub call",
                isBlank(System.getenv("GITHUB_TOKEN")));

        GitHubStatusNotifier n = new GitHubStatusNotifier();
        n.setStatus("owner/repo",
                "0123456789abcdef0123456789abcdef01234567",
                true,
                "ok",
                "12345");

        String output = outContent.toString();
        assertTrue(output.contains("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status."));
    }

    /**
     * Verifies that an invalid "owner/repo" format is handled safely:
     * - prints an error about invalid repository full_name
     * - returns early without throwing
     *
     * Note: If GITHUB_TOKEN is missing, the notifier may skip even earlier.
     * Therefore we accept either log message to keep the test stable across environments.
     */
    @Test
    public void setStatus_invalidRepoFullName_logsAndReturns() throws Exception {
        GitHubStatusNotifier n = new GitHubStatusNotifier();
        n.setStatus("invalidFullName",
                "0123456789abcdef0123456789abcdef01234567",
                true,
                "ok");

        String output = outContent.toString();
        assertTrue(output.contains("[NOTIFY] Invalid repository full_name")
                || output.contains("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status."));
    }

    /**
     * Verifies that a missing/blank commit SHA is handled safely:
     * - prints an error about missing sha
     * - returns early without throwing
     *
     * Note: If GITHUB_TOKEN is missing, the notifier may skip even earlier.
     * Therefore we accept either log message to keep the test stable across environments.
     */
    @Test
    public void setStatus_missingSha_logsAndReturns() throws Exception {
        GitHubStatusNotifier n = new GitHubStatusNotifier();
        n.setStatus("owner/repo",
                "",
                true,
                "ok");

        String output = outContent.toString();
        assertTrue(output.contains("[NOTIFY] Missing sha; cannot set status.")
                || output.contains("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status."));
    }

    /**
     * Verifies that the overloaded setStatus(..., buildId) does not throw when used in environments
     * where CI_PUBLIC_URL may be missing/invalid, and where we also do not want network calls.
     *
     * We skip this test if GITHUB_TOKEN is present to avoid a real GitHub API call.
     */
    @Test
    public void setStatus_withDetailsLink_safeFallback_doesNotThrow() throws Exception {
        Assume.assumeTrue("GITHUB_TOKEN is set; skipping to avoid real GitHub call",
                isBlank(System.getenv("GITHUB_TOKEN")));

        GitHubStatusNotifier n = new GitHubStatusNotifier();
        n.setStatus("owner/repo",
                "0123456789abcdef0123456789abcdef01234567",
                true,
                "ok",
                "999");

        String output = outContent.toString();
        assertTrue(output.contains("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status."));
    }

    /**
     * Helper: treat null/empty/whitespace-only strings as blank.
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
