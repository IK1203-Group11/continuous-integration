import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Skeleton of a ContinuousIntegrationServer which acts as webhook
 * See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler {
    private final GitHubPayloadParser payloadParser = new GitHubPayloadParser();
    
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final MetricsService metricsService = new MetricsService();

    public static MetricsService getMetricsService() {
        return metricsService;
    }

    
    // We persist build metadata to disk so that history survives server restarts.
    // Each build writes:
    //   builds/<buildId>/meta.json  (commit, branch, timestamps, status, log URL)
    //   builds/<buildId>/log.txt    (already stored by BuildExecutor)
    // A dedicated endpoint lists all builds:
    //   GET /builds  -> clickable list of builds + links to /build/<buildId>
    private static Path buildsDir() {
        return Paths.get("builds");
    }

    // P7: minimal build metadata model (stored as JSON on disk).
    private static class BuildMeta {
        public String buildId;
        public String branch;
        public String commitSha;
        public String repoFullName;
        public String status;      // "success" or "failure"
        public String startedAt;   // ISO-8601
        public String finishedAt;  // ISO-8601
        public String logUrl;      // "/build/<id>"
    }

    // P7: write metadata to builds/<buildId>/meta.json
    private static void writeMeta(BuildMeta meta) throws IOException {
        Path dir = buildsDir().resolve(meta.buildId);
        Files.createDirectories(dir);
        Path metaPath = dir.resolve("meta.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), meta);
    }

    // P7: read all build metadata from builds/*/meta.json and sort newest first.
    private static List<BuildMeta> readAllMetas() {
        try {
            if (!Files.exists(buildsDir())) return List.of();

            return Files.list(buildsDir())
                    .filter(Files::isDirectory)
                    .map(d -> d.resolve("meta.json"))
                    .filter(Files::exists)
                    .map(p -> {
                        try {
                            return MAPPER.readValue(p.toFile(), BuildMeta.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(m -> m != null)
                    .sorted(Comparator.comparing((BuildMeta m) -> m.finishedAt).reversed())
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Called when a request is received.
     *
     * Reads the JSON payload from the request body, checks the event type (push, ping, etc.)
     * If it's a PUSH event, parses the JSON to extract branch name, commit SHA and clone URL.
     * .
     * .
     * .
     * PERFORMS CI TASKS
     */
    public void handle(String target,
                       Request baseRequest,
                       HttpServletRequest request,
                       HttpServletResponse response)
            throws IOException, ServletException {

        response.setContentType("text/html;charset=utf-8");
        baseRequest.setHandled(true);

        // P7: Build history page (persistent list of all builds).
        // The grader can open this URL and click individual builds.
        //   GET /builds -> shows commit id, date, status, and link to build log (/build/<id>)
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/builds".equals(target)) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);

            List<BuildMeta> metas = readAllMetas();

            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>Build History</title></head><body>");
            html.append("<h2>Build History</h2>");

            if (metas.isEmpty()) {
                html.append("<p>No builds yet.</p>");
            } else {
                html.append("<ul>");
                for (BuildMeta m : metas) {
                    String icon = "success".equals(m.status) ? "success" : "fail";
                    String shortSha = (m.commitSha == null) ? "" : m.commitSha.substring(0, Math.min(12, m.commitSha.length()));
                    html.append("<li>")
                            .append(icon).append(" ")
                            .append("<b>").append(m.repoFullName).append("</b> ")
                            .append("branch=").append(m.branch).append(" ")
                            .append("sha=").append(shortSha).append(" ")
                            .append("finished=").append(m.finishedAt).append(" — ")
                            .append("<a href=\"").append(m.logUrl).append("\">log</a>")
                            .append("</li>");
                }
                html.append("</ul>");
            }

            html.append("</body></html>");
            response.getWriter().println(html.toString());
            return;
        }
        //P7: Remarkable feature - CI Metrics and Health Endpoints
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/metrics".equals(target)) {
            response.setContentType("text/html;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);

            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>Metrics</title></head><body>");
            html.append("<h2>Metrics</h2>");
            html.append("<p>Total builds: ").append(metricsService.getTotalBuilds()).append("</p>");
            html.append("<p>Successful builds: ").append(metricsService.getSucessfulBuilds()).append("</p>");
            html.append("<p>Failed builds: ").append(metricsService.getFailedBuilds()).append("</p>");
            html.append("<p>Average build duration (ms): ").append(metricsService.getAvgBuildDuration()).append("</p>");
            html.append("<p>Success rate: ").append(metricsService.getSuccessRate()).append("%</p>");
            html.append("<p>Failure rate: ").append(metricsService.getFailureRate()).append("%</p>");
            html.append("</body></html>");

            response.getWriter().println(html.toString());
        }
        
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/health".equals(target)) {
            response.setContentType("text/plain;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);

            boolean healthy = true;

            if (metricsService.getTotalBuilds() > 0 && metricsService.getFailureRate() > 50.0) {
                healthy = false;

            }
            response.setStatus(healthy ? 
                HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);

            String json = String.format(
                "{\n" +
                "  \"status\": \"%s\",\n" +
                "  \"uptimeSeconds\": %.0f,\n" +
                "  \"lastBuildTimestamp\": %d\n" +
                "}",
                healthy ? "OK" : "UNHEALTHY",
                java.lang.management.ManagementFactory
                    .getRuntimeMXBean()
                    .getUptime() / 1000.0,
                metricsService.getLastBuildTimeStamp()
            );
            response.getWriter().println(json);
            return;
        }

        // Log endpoint for GitHub "Details" link:
        // When the commit status includes a target_url like https://<public-url>/build/<buildId>,
        // GitHub will open that URL when the user clicks "Details".
        // This handler serves the stored log file for a specific build id at:
        //   GET /build/<buildId>  ->  builds/<buildId>/log.txt
        if ("GET".equalsIgnoreCase(request.getMethod()) && target.startsWith("/build/")) {
            String id = target.substring("/build/".length());
            java.nio.file.Path logPath = java.nio.file.Paths.get("builds", id, "log.txt");

            response.setContentType("text/plain;charset=utf-8");
            baseRequest.setHandled(true);

            if (!java.nio.file.Files.exists(logPath)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().println("No log for buildId=" + id);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(java.nio.file.Files.readString(logPath));
            return;
        }

        // GitHub webhooks will be POST requests with a JSON body
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.getWriter().println("Only POST requests are allowed");
            return;
        }

        // Read JSON payload from request body.
        String body = request.getReader().lines().collect(Collectors.joining("\n"));

        // Check what type of event (push, ping, etc.)
        String event = request.getHeader("X-GitHub-Event");
        System.out.println("Received event: " + event);
        System.out.println("Target: " + target);

        try {
            // Handle GitHub ping event (when first adding webhook).
            if ("ping".equalsIgnoreCase(event)) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().println("Pong (ping received)");
                return;
            }

            // Parse JSON and extract fields from push event.
            GitHubPayloadParser.BuildTrigger trigger = payloadParser.parsePushPayload(body);

            System.out.println("Branch: " + trigger.branch);
            System.out.println("Commit: " + trigger.commitSha);
            System.out.println("Clone URL: " + trigger.cloneUrl);

            JsonNode root = MAPPER.readTree(body);
            String fullName = root.path("repository").path("full_name").asText("");
            System.out.println("Repo full_name: " + fullName);

            // P7: capture build timestamps (stored in meta.json for persistent history)
            Instant startedAt = Instant.now();

            // Run the tests on related branch and produce the result of tests.
            // We also record metrics about the build execution (success/failure, duration) in the MetricsService.
            BuildExecutor executor = new BuildExecutor(metricsService);
            boolean result = executor.runBuild(trigger.cloneUrl, trigger.branch);
            System.out.println(result ? "Tests passed." : "Tests failed.");

            Instant finishedAt = Instant.now();

            // P7: persist build metadata on disk so history survives restarts.
            // meta.json is stored alongside the build log:
            //   builds/<buildId>/meta.json
            //   builds/<buildId>/log.txt
            try {
                String buildIdForMeta = executor.getLastBuildId();
                if (buildIdForMeta != null && !buildIdForMeta.isBlank()) {
                    BuildMeta meta = new BuildMeta();
                    meta.buildId = buildIdForMeta;
                    meta.branch = trigger.branch;
                    meta.commitSha = trigger.commitSha;
                    meta.repoFullName = fullName;
                    meta.status = result ? "success" : "failure";
                    meta.startedAt = startedAt.toString();
                    meta.finishedAt = finishedAt.toString();
                    meta.logUrl = "/build/" + buildIdForMeta;
                    writeMeta(meta);
                }
            } catch (Exception e) {
                System.out.println("[P7] warning: could not persist build metadata: " + e.getMessage());
            }

            try {
                if (fullName.isBlank()) {
                    System.out.println("[NOTIFY] repository.full_name missing; cannot set commit status.");
                } else {
                    GitHubStatusNotifier notifier = new GitHubStatusNotifier();
                    String buildId = executor.getLastBuildId();
                    notifier.setStatus(fullName, trigger.commitSha, result, result ? "build and tests passed" : "build or tests failed", buildId);
                }
            } catch (Exception e) {
                System.out.println("[NOTIFY] warning: could not set commit status: " + e.getMessage());
            }

            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("Parsed branch=" + trigger.branch + " sha=" + trigger.commitSha);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().println("Failed to parse webhook payload: " + e.getMessage());
        }
    }

    // used to start the CI server in command line
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer());
        server.start();
        server.join();
    }
}
