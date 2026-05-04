package com.acme.modres;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Health check endpoint for container orchestration platforms (AWS ECS/EKS)
 * Returns HTTP 200 with JSON status when application is healthy
 */
@WebServlet({ "/health", "/actuator/health" })
public class HealthCheckServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    
    PrintWriter out = response.getWriter();
    out.print("{\"status\":\"UP\",\"application\":\"ModResorts\",\"version\":\"2.0.0\"}");
    out.flush();
  }

  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    // Support HEAD requests for health checks
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
