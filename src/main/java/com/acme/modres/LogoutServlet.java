package com.acme.modres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Cloud-native logout servlet that uses standard session management
 * instead of WebSphere-specific clustering features.
 * Session state is externalized to GCP Memorystore for Redis for
 * cloud-native horizontal scaling.
 */
@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // Use standard servlet session management
      // Session state is externalized to Memorystore for Redis via Spring Session
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      
      // Clear any authentication cookies
      javax.servlet.http.Cookie[] cookies = request.getCookies();
      if (cookies != null) {
        for (javax.servlet.http.Cookie cookie : cookies) {
          if (cookie.getName().startsWith("JSESSIONID") || 
              cookie.getName().startsWith("SESSION")) {
            cookie.setValue("");
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);
          }
        }
      }
    } catch (Exception e) {
      System.err.println("[ERROR] Error logging out: " + e.getMessage());
      e.printStackTrace();
    }

    response.sendRedirect("login.jsp");
  }
}
