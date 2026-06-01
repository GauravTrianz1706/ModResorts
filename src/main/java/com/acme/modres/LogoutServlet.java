package com.acme.modres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// Replaced com.ibm.websphere.security.WSSecurityHelper (WebSphere-specific, blocker-1/blocker-6)
// with standard Jakarta/Java EE HttpSession invalidation for container-native deployment

import java.io.IOException;

@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // Replaced WSSecurityHelper.revokeSSOCookies() with standard session invalidation
      // for container-native deployment on AWS ECS/EKS (blocker-1, blocker-6)
      if (request.getSession(false) != null) {
        request.getSession(false).invalidate();
      }
      // Clear any SSO cookies using standard Servlet API
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
