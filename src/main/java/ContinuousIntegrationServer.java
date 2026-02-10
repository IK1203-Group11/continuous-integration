import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

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

        if ("GET".equalsIgnoreCase(request.getMethod())) {
            // GET /builds -> list all builds
            if ("/builds".equals(target)) {
                Path historyDir = Paths.get("build-history");

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/html;charset=utf-8");

                String baseUrl =
                        request.getScheme() + "://" +
                                request.getServerName() +
                                ((request.getServerPort() == 80 || request.getServerPort() == 443)
                                        ? ""
                                        : ":" + request.getServerPort());

                PrintWriter out = response.getWriter();

                out.println("<html><body>");
                out.println("<h2>Build History</h2>");

                if (!Files.exists(historyDir)) {
                    out.println("<p>No builds found.</p>");
                    out.println("</body></html>");
                    return;
                }

                out.println("<table border='1' cellpadding='5' cellspacing='0'>");
                out.println("<tr>");
                out.println("<th>Build ID</th>");
                out.println("<th>Branch</th>");
                out.println("<th>Start Time</th>");
                out.println("<th>Status</th>");
                out.println("</tr>");

                try (DirectoryStream<Path> builds = Files.newDirectoryStream(historyDir)) {
                    for (Path buildDir : builds) {
                        if (!Files.isDirectory(buildDir)) continue;

                        Path meta = buildDir.resolve("meta.txt");
                        if (!Files.exists(meta)) continue;

                        String buildId = buildDir.getFileName().toString();
                        String branch = "-";
                        String startedAt = "-";
                        String success = "-";

                        for (String line : Files.readAllLines(meta)) {
                            if (line.startsWith("branch=")) branch = line.substring(7);
                            if (line.startsWith("startedAt=")) startedAt = line.substring(10);
                            if (line.startsWith("success=")) success = line.substring(8);
                        }

                        out.println("<tr>");
                        out.println("<td><a href='" + baseUrl + "/builds/" + buildId + "'>"
                                + buildId + "</a></td>");
                        out.println("<td>" + branch + "</td>");
                        out.println("<td>" + startedAt + "</td>");
                        out.println("<td>" + success + "</td>");
                        out.println("</tr>");
                    }
                }

                out.println("</table>");
                out.println("</body></html>");
                return;
            }

            // GET /builds/{id} → show build details
            if (target.matches("^/builds/[^/]+$")) {
                String buildId = target.substring("/builds/".length());
                Path buildDir = Paths.get("build-history", buildId);

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain");

                if (!Files.exists(buildDir)) {
                    response.getWriter().println("Build not found: " + buildId);
                    return;
                }

                Path meta = buildDir.resolve("meta.txt");
                Path log = buildDir.resolve("build.log");

                response.getWriter().println("------- METADATA ------");
                if (Files.exists(meta)) {
                    Files.lines(meta).forEach(line -> {
                        try {
                            response.getWriter().println(line);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }

                response.getWriter().println("\n------ LOG ------");
                if (Files.exists(log)) {
                    Files.lines(log).forEach(line -> {
                        try {
                            response.getWriter().println(line);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
                return;
            }
            // Unknown GET Endpoint
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().println("Unknown GET endpoint: " + target);
            return;
        }

        // GitHub webhooks will be POST requests with a JSON body
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.getWriter().println("Only POST and GET requests are allowed");
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

            // Run the tests on related branch and produce the result of tests.
            BuildExecutor executor = new BuildExecutor();
            boolean result = executor.runBuild(trigger.cloneUrl, trigger.branch);
            System.out.println(result ? "Tests passed." : "Tests failed.");


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