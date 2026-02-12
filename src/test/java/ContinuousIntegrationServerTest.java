import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for testing the handle() function in ContinuousIntegrationServer.java.
 * Contains two tests that use the mock oject from Mockito.
 */
public class ContinuousIntegrationServerTest {
    private static final String TEST_SECRET = "test-secret";
    @BeforeClass
    public static void setSecret() {
        System.setProperty("GITHUB_WEBHOOK_SECRET", TEST_SECRET);
    }
    private static BufferedReader readerOf(String body) {
        return new BufferedReader(new StringReader(body));
    }

    private static String sig256(String secret, String body) {
        return "sha256=" + GitHubWebhookVerifier.hmacSha256Hex(secret, body);
    }
    
    /**
     * Asserts that in the event that the handle() function receives a Ping request,
     * it should respond with "Pong (ping received)" and code 200.
     * @throws Exception 
     */
    @Test
    public void pingEvent_ReturnsPongAnd200() throws Exception {
        ContinuousIntegrationServer server = new ContinuousIntegrationServer();

        // Use one mock for both baseRequest and request
        Request req = mock(Request.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);

        // Writer to capture response output
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        when(resp.getWriter()).thenReturn(pw);

        // Stub request values
        when(req.getMethod()).thenReturn("POST");
        when(req.getHeader("X-GitHub-Event")).thenReturn("ping");

        // Read JSON payload from request body.
        String body = "{}";
        when(req.getReader()).thenReturn(readerOf(body));

        // Add valid webhook signature header (required after signature enforcement)
        String secret = System.getenv("GITHUB_WEBHOOK_SECRET");
        when(req.getHeader("X-Hub-Signature-256")).thenReturn(sig256(TEST_SECRET, body));

        // Call handle: pass req as both baseRequest and request
        server.handle("/", req, req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
        assertTrue(sw.toString().contains("Pong (ping received)"));
    }

    /**
     * Asserts that in the event that the handle() function receives an invalid payload,
     * it should return error code 400 and a expected failur message.
     * @throws Exception
     */
    @Test
    public void postPushWithInvalidPayload_Returns400() throws Exception {
        ContinuousIntegrationServer server = new ContinuousIntegrationServer();

        Request baseRequest = mock(Request.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        when(response.getWriter()).thenReturn(pw);

        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-GitHub-Event")).thenReturn("push");

        // Invalid push payload: missing ref/after/repository.clone_url --> parser throws exception
        String body = "{\"repository\":{}}";
        when(request.getReader()).thenReturn(readerOf(body));

        // Add valid webhook signature header (required after signature enforcement)
        String secret = System.getProperty("GITHUB_WEBHOOK_SECRET", System.getenv("GITHUB_WEBHOOK_SECRET"));
        when(request.getHeader("X-Hub-Signature-256")).thenReturn(sig256(secret, body));

        // Act
        server.handle("/", baseRequest, request, response);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        String out = sw.toString();
        assertTrue("Expected failure message, got: " + out, out.contains("Failed to parse webhook payload"));
    }
}
