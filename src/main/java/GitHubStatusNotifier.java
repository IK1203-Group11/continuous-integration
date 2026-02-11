import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Sends CI build result back to GitHub as a commit status (success/failure).
 *
 * Requires environment variable:
 *   GITHUB_TOKEN = GitHub Personal Access Token with permission to set commit statuses.
 */
public class GitHubStatusNotifier {

    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * @param fullName    "owner/repo" (from webhook: repository.full_name)
     * @param sha         commit SHA to set status on
     * @param success     true => success, false => failure
     * @param description short message shown in GitHub UI
     */
    public void setStatus(String fullName, String sha, boolean success, String description) throws Exception {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.out.println("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status.");
            return;
        }

        if (fullName == null || fullName.isBlank() || !fullName.contains("/")) {
            System.out.println("[NOTIFY] Invalid repository full_name (expected owner/repo): " + fullName);
            return;
        }
        if (sha == null || sha.isBlank()) {
            System.out.println("[NOTIFY] Missing sha; cannot set status.");
            return;
        }

        String state = success ? "success" : "failure";
        String apiUrl = "https://api.github.com/repos/" + fullName + "/statuses/" + sha;

        if (description == null) description = "";
        if (description.length() > 120) description = description.substring(0, 120);

        // context is what appears in GitHub checks UI
        String json = "{"
                + "\"state\":\"" + escapeJson(state) + "\","
                + "\"context\":\"ci-server\","
                + "\"description\":\"" + escapeJson(description) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            System.out.println("[NOTIFY] Set GitHub commit status: " + state);
        } else {
            System.out.println("[NOTIFY] Failed to set status: " + resp.statusCode() + " body=" + resp.body());
        }
    }

    /**
     * Same as setStatus(...) but also attaches a "Details" link pointing to the build log endpoint:
     *   CI_PUBLIC_URL + "/build/" + buildId
     *
     * Requires environment variable:
     *   CI_PUBLIC_URL = https://your-ngrok-url (or any public base URL)
     *
     * If CI_PUBLIC_URL is not set (or invalid), it falls back to the normal status without target_url.
     * @param fullName the owner/repo string
     * @param sha the commit hash
     * @param success the build result
     * @param description status message
     * @param buildId the unique build identifier
     * @throws Exception if the HTTP request fails
     */
    public void setStatus(String fullName, String sha, boolean success, String description, String buildId) throws Exception {
        String publicBase = System.getenv("CI_PUBLIC_URL");

        // If we cannot build a valid target_url, fall back to original method (unchanged behavior)
        if (publicBase == null || publicBase.isBlank() || buildId == null || buildId.isBlank()) {
            setStatus(fullName, sha, success, description);
            return;
        }

        publicBase = publicBase.trim();

        // Strip accidental quotes: set CI_PUBLIC_URL="https://...."
        if (publicBase.length() >= 2 &&
                ((publicBase.startsWith("\"") && publicBase.endsWith("\"")) ||
                 (publicBase.startsWith("'") && publicBase.endsWith("'")))) {
            publicBase = publicBase.substring(1, publicBase.length() - 1).trim();
        }

        // Must be http(s) or GitHub returns 422
        if (!(publicBase.startsWith("http://") || publicBase.startsWith("https://"))) {
            setStatus(fullName, sha, success, description);
            return;
        }

        publicBase = publicBase.replaceAll("/+$", "");
        String targetUrl = publicBase + "/build/" + buildId;

        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isBlank()) {
            System.out.println("[NOTIFY] No GITHUB_TOKEN set; skipping GitHub status.");
            return;
        }

        if (fullName == null || fullName.isBlank() || !fullName.contains("/")) {
            System.out.println("[NOTIFY] Invalid repository full_name (expected owner/repo): " + fullName);
            return;
        }
        if (sha == null || sha.isBlank()) {
            System.out.println("[NOTIFY] Missing sha; cannot set status.");
            return;
        }

        String state = success ? "success" : "failure";
        String apiUrl = "https://api.github.com/repos/" + fullName + "/statuses/" + sha;

        if (description == null) description = "";
        if (description.length() > 120) description = description.substring(0, 120);

        // context is what appears in GitHub checks UI
        String json = "{"
                + "\"state\":\"" + escapeJson(state) + "\","
                + "\"context\":\"ci-server\","
                + "\"description\":\"" + escapeJson(description) + "\","
                + "\"target_url\":\"" + escapeJson(targetUrl) + "\""
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            System.out.println("[NOTIFY] Set GitHub commit status: " + state + " (with Details link)");
        } else {
            System.out.println("[NOTIFY] Failed to set status: " + resp.statusCode() + " body=" + resp.body());
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
