package com.studentapp;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

@WebServlet(name = "NumberGuessServlet", urlPatterns = {"/NumberGuess"})
public class NumberGuessServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final Random rng = new Random();

    /** Exposed for tests: returns a number in [1, 5]. */
    public int getTargetNumber() {
        return rng.nextInt(5) + 1;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><head><title>Number Guess Game</title></head><body>");
            out.println("<h2>Guess a number between 1 and 5</h2>");
            out.println("<form method='post' action='NumberGuess'>");
            out.println("<input type='number' name='guess' min='1' max='5' required/>");
            out.println("<button type='submit'>Try</button>");
            out.println("</form>");
            out.println("</body></html>");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");

        int guess = 0;
        try {
            String g = req.getParameter("guess");
            if (g != null) {
                guess = Integer.parseInt(g.trim());
            }
        } catch (NumberFormatException ignored) { }

        int target = getTargetNumber();
        boolean win = (guess == target);

        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><head><title>Number Guess Game</title></head><body>");
            out.printf("<p>Your guess: %d | Target: %d</p>%n", guess, target);
            if (win) {
                out.println("<p style='color:green'>You win</p>");
            } else {
                out.println("<p style='color:red'>Try again</p>");
            }
            out.println("<p><a href='NumberGuess'>Play again</a> | <a href='index.jsp'>Home</a></p>");
            out.println("</body></html>");
        }
    }
}
