import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import static org.junit.Assert.*;

import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.junit.Test;
import org.mockito.ArgumentCaptor;


public class ContinuousIntegrationServerTest {
    
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
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader("{}")));

        // Call handle: pass req as both baseRequest and request
        server.handle("/", req, req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
        assertTrue(sw.toString().contains("Pong (ping received)"));
    }

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
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("{\"repository\":{}}")));

        // Act
        server.handle("/", baseRequest, request, response);

        // Assert
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);

        String out = sw.toString();
        assertTrue("Expected failure message, got: " + out, out.contains("Failed to parse webhook payload"));
    }
}
