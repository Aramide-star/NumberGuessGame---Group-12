package com.studentapp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;

public class NumberGuessServletTest {

    /** Deterministic subclass so tests don't depend on randomness. */
    private static class TestableServlet extends NumberGuessServlet {
        private final int fixedTarget;
        TestableServlet(int fixedTarget) { this.fixedTarget = fixedTarget; }
        @Override
        public int getTargetNumber() { return fixedTarget; }
    }

    private NumberGuessServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @Before
    public void setUp() throws Exception {
        // Fix target to 50 so we can test low/high/correct reliably
        servlet = new TestableServlet(50);
        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    public void testGuessTooLow() throws Exception {
        Mockito.when(request.getParameter("guess")).thenReturn("1");
        servlet.doPost(request, response);
        String out = responseWriter.toString();
        assertTrue("Should prompt to try again when guess is too low", out.contains("Try again"));
    }

    @Test
    public void testGuessTooHigh() throws Exception {
        Mockito.when(request.getParameter("guess")).thenReturn("100");
        servlet.doPost(request, response);
        String out = responseWriter.toString();
        assertTrue("Should prompt to try again when guess is too high", out.contains("Try again"));
    }

    @Test
    public void testCorrectGuess() throws Exception {
        Mockito.when(request.getParameter("guess")).thenReturn("50");
        servlet.doPost(request, response);
        String out = responseWriter.toString();
        assertTrue("Should congratulate on correct guess", out.contains("You win"));
    }
}
