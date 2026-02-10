import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import static org.junit.Assert.*;

public class GitHubPayloadParserTest {
    private final GitHubPayloadParser parser = new GitHubPayloadParser();

    /**
     * Test that a valid push event payload is parsed correctly,
     * and that the branch name, commit SHA and clone URL are extracted correctly.
     * @throws Exception
     */
    @Test
    public void parsePushpayload_validPayload_extractsBranchShaAndCloneUrl() throws Exception {
        String json = "{\n" +
                "  \"ref\": \"refs/heads/assessment\",\n" +
                "  \"after\": \"0123456789abcdef0123456789abcdef01234567\",\n" +
                "  \"repository\": {\n" +
                "    \"clone_url\": \"https://github.com/example-org/example-repo.git\"\n" +
                "  }\n" +
                "}";

        GitHubPayloadParser.BuildTrigger t = parser.parsePushPayload(json);

        assertEquals("assessment", t.branch);
        assertEquals("0123456789abcdef0123456789abcdef01234567", t.commitSha);
        assertEquals("https://github.com/example-org/example-repo.git", t.cloneUrl);
    }

    /**
     * Test that a branch name with slashes is parsed correctly
     * (e.g. "refs/heads/feature/my-branch" should give "feature/my-branch").
     * @throws Exception
     */
    @Test
    public void parsePushPayload_branchWithSlash_isParsedCorrectly() throws Exception {
        String json = "{\n" +
                "  \"ref\": \"refs/heads/feature/my-branch\",\n" +
                "  \"after\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\n" +
                "  \"repository\": {\n" +
                "    \"clone_url\": \"https://github.com/a/b.git\"\n" +
                "  }\n" +
                "}";

        GitHubPayloadParser.BuildTrigger t = parser.parsePushPayload(json);

        assertEquals("feature/my-branch", t.branch);
    }

    /**
     * Test that if ref field does not start with "refs/heads/",
     * parser should not remove any prefix and just return the ref value as branch name.
     * @throws Exception
     */
    @Test
    public void parsePushPayload_refWithoutPrefix_keepsValue() throws Exception {
        String json = "{\n" +
                "  \"ref\": \"assessment\",\n" +
                "  \"after\": \"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\n" +
                "  \"repository\": {\n" +
                "    \"clone_url\": \"https://github.com/a/b.git\"\n" +
                "  }\n" +
                "}";

        GitHubPayloadParser.BuildTrigger t = parser.parsePushPayload(json);

        assertEquals("assessment", t.branch);
    }

    /**
     * Test that if any of the required fields (ref, after, repository, clone url)
     * are missing or empty - parser should throw IllegalArgumentException.
     */
    @Test
    public void parsePushPayload_missingRef_throwsIllegalArgumentException() {
        String json = "{\n" +
                "  \"after\": \"cccccccccccccccccccccccccccccccccccccccc\",\n" +
                "  \"repository\": {\n" +
                "    \"clone_url\": \"https://github.com/a/b.git\"\n" +
                "  }\n" +
                "}";

        try {
            parser.parsePushPayload(json);
            fail("Expected IllegalArgumentException due to missing ref");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing required fields"));
        } catch (Exception other) {
            fail("Expected IllegalArgumentException, got: " + other);
        }
    }

    /**
     * Test that if "after" field is missing or empty, parser should throw IllegalArgumentException.
     */
    @Test
    public void parsePushPayload_missingAfter_throwsIllegalArgumentException() {
        String json = "{\n" +
                "  \"ref\": \"refs/heads/assessment\",\n" +
                "  \"repository\": {\n" +
                "    \"clone_url\": \"https://github.com/a/b.git\"\n" +
                "  }\n" +
                "}";

        try {
            parser.parsePushPayload(json);
            fail("Expected IllegalArgumentException due to missing after");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing required fields"));
        } catch (Exception other) {
            fail("Expected IllegalArgumentException, got: " + other);
        }
    }

    /**
     * Test that if "clone_url" field is missing or empty, parser should throw IllegalArgumentException.
     */
    @Test
    public void parsePushPayload_missingCloneUrl_throwsIllegalArgumentException() {
        String json = "{\n" +
                "  \"ref\": \"refs/heads/assessment\",\n" +
                "  \"after\": \"dddddddddddddddddddddddddddddddddddddddd\",\n" +
                "  \"repository\": {}\n" +
                "}";

        try {
            parser.parsePushPayload(json);
            fail("Expected IllegalArgumentException due to missing clone_url");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing required fields"));
        } catch (Exception other) {
            fail("Expected IllegalArgumentException, got: " + other);
        }
    }
}
