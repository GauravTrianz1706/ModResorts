package com.acme.modres;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Health check endpoint for containerized deployment.
 * Returns a simple JSON response indicating the application status.
 * This endpoint is used by container orchestrators (like Kubernetes) 
 * to determine if the application is healthy and ready to receive traffic.
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
    out.print("{\"status\":\"UP\",\"application\":\"ModResorts\"}");
    out.flush();
  }
  
  @Override
  protected void doHead(HttpServletRequest request, HttpServletResponse response) 
      throws ServletException, IOException {
    response.setStatus(HttpServletResponse.SC_OK);
  }
}
