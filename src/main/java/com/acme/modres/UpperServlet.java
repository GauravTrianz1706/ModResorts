package com.acme.modres;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Cloud-native servlet that uses standard Java APIs instead of
 * WebSphere-specific utilities for response encoding.
 */
@WebServlet("/resorts/upper")
public class UpperServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("text/html");

    String originalStr = request.getParameter("input");
    if (originalStr == null) {
      originalStr = "";
    }

    String newStr = originalStr.toUpperCase();
    
    // Use standard Java URLEncoder instead of WebSphere-specific ResponseUtils
    // This provides cross-platform compatibility for cloud deployment
    newStr = URLEncoder.encode(newStr, StandardCharsets.UTF_8.toString());

    // Use try-with-resources for automatic resource management
    try (PrintWriter out = response.getWriter()) {
      out.print("<br/><b>upper case input " + newStr + "</b>");
    }
  }
}
