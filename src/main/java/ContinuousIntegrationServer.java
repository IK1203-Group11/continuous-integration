import java.io.IOException;
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

            // Run the tests on related branch and produce the result of tests.
            BuildExecutor executor = new BuildExecutor();
            boolean result = executor.runBuild(trigger.cloneUrl, trigger.branch);
            System.out.println(result ? "Tests passed." : "Tests failed.");

            try {
                if (fullName.isBlank()) {
                    System.out.println("[NOTIFY] repository.full_name missing; cannot set commit status.");
                } else {
                    GitHubStatusNotifier notifier = new GitHubStatusNotifier();
                    notifier.setStatus(fullName, trigger.commitSha, result, result ? "build and tests passed" : "build or tests failed");
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