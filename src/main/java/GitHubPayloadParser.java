import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses GitHub webhook JSON payloads to extract relevant information for triggering CI build.
 */
public class GitHubPayloadParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Data class to hold extracted information from push event payload.
     * Used as return type of parsePushPayload method.
     */
    public static class BuildTrigger {
        public final String branch;
        public final String commitSha;
        public final String cloneUrl;

        public BuildTrigger(String branch, String commitSha, String cloneUrl) {
            this.branch = branch;
            this.commitSha = commitSha;
            this.cloneUrl = cloneUrl;
        }
    }

    /**
     * Parses JSON payload from GitHub push event and extracts branch name, commit SHA and clone URL.
     * @param body JSON string from request body
     * @return BuildTrigger object containing extracted info.
     * @throws IOException if JSON parsing fails, or IllegalArgumentException if required fields are missing or empty.
     */
    public BuildTrigger parsePushPayload(String body) throws IOException {
        // Parse JSON and extract fields.
        JsonNode root = MAPPER.readTree(body);

        // Extract ref, after and clone_url fields as Strings. If field is missing, returns empty string.
        String ref = root.path("ref").asText("");
        String branch = toBranchName(ref);

        String sha = root.path("after").asText("");
        String cloneUrl = root.path("repository").path("clone_url").asText("");

        // If any of the required fields are missing or empty, throw exception.
        if (branch.isBlank() || sha.isBlank() || cloneUrl.isBlank()) {
            throw new IllegalArgumentException("Missing required fields in payload. ref = " + ref + ", after = " + sha
                    + ", clone_url = " + cloneUrl);
        }

        return new BuildTrigger(branch, sha, cloneUrl);
    }

    /**
     * Converts GitHub ref string to branch name. If ref starts with "refs/heads/", prefix is removed.
     * @param ref the ref string from GitHub payload.
     * @return branch name extracted from ref, or empty string if ref is null or empty.
     */
    static String toBranchName(String ref) {
        String prefix = "refs/heads/";
        if (ref != null && ref.startsWith(prefix)) {
            return ref.substring(prefix.length());
        }
        return ref == null ? "" : ref; // return empty string if ref is null.
    }
}
