package com.acme.modres;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Logout servlet migrated from WebSphere-specific APIs to standard servlet APIs
 * for containerized deployment.
 * 
 * Fixes:
 * - blocker-1: Replaced WSSecurityHelper.revokeSSOCookies with standard session invalidation
 * - blocker-6: Removed WebSphere server-specific dependencies
 */
@WebServlet({ "/logout" })
public class LogoutServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    try {
      // Replace WebSphere-specific WSSecurityHelper.revokeSSOCookies
      // with standard servlet session invalidation
      HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      
      // Clear authentication cookies using standard servlet API
      Cookie[] cookies = request.getCookies();
      if (cookies != null) {
        for (Cookie cookie : cookies) {
          if (cookie.getName().startsWith("JSESSIONID") || 
              cookie.getName().startsWith("SSO") ||
              cookie.getName().contains("auth")) {
            Cookie clearCookie = new Cookie(cookie.getName(), "");
            clearCookie.setMaxAge(0);
            clearCookie.setPath(cookie.getPath() != null ? cookie.getPath() : "/");
            response.addCookie(clearCookie);
          }
        }
      }
    } catch (Exception e) {
      System.err.println("[ERROR] Error logging out");
      e.printStackTrace();
    }

    response.sendRedirect("login.jsp");
  }
}
