import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.stream.Collectors;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 * Skeleton of a ContinuousIntegrationServer which acts as webhook
 * See the Jetty documentation for API documentation of those classes.
 */
public class ContinuousIntegrationServer extends AbstractHandler {
    private final GitHubPayloadParser payloadParser = new GitHubPayloadParser();

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

            // DO ALL CI TASKS HERE
            // Clone repo,
            // checkout branch,
            // compile code, run tests...

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