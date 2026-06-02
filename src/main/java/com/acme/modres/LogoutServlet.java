package com.acme.modres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// Replaced WebSphere-specific com.ibm.websphere.security.WSSecurityHelper with standard servlet session invalidation
// blocker-1 (cz-java-0075): WebSphere Specific Features - migrated to Spring Boot standard abstractions
// blocker-6 (cz-java-0081): Server and Dependencies - removed WebSphere server-specific dependency

import java.io.IOException;

@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // Replaced WSSecurityHelper.revokeSSOCookies() with standard servlet session invalidation
      // for container-native deployment on AWS ECS/EKS
      if (request.getSession(false) != null) {
        request.getSession(false).invalidate();
      }
      // Clear authentication cookies using standard servlet API
      javax.servlet.http.Cookie[] cookies = request.getCookies();
      if (cookies != null) {
        for (javax.servlet.http.Cookie cookie : cookies) {
          cookie.setMaxAge(0);
          cookie.setPath("/");
          response.addCookie(cookie);
        }
      }
    } catch (Exception e) {
      System.err.println("[ERROR] Error logging out");
      e.printStackTrace();
    }

    response.sendRedirect("login.jsp");
  }
}
