package com.acme.modres;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(LogoutServlet.class.getName());

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // IBM WebSphere specific security helper removed for Java 17 compatibility
      // Using standard servlet session invalidation instead
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      logger.info("User logged out successfully");
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error logging out", e);
    }

    response.sendRedirect("login.jsp");
  }
}
