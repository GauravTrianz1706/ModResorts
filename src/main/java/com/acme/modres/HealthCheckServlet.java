package com.acme.modres;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Health check endpoint for container liveness and readiness probes.
 * Required for containerized deployment on AWS ECS/EKS.
 *
 * GET /health - Returns HTTP 200 with JSON status for container health checks.
 */
@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);

    PrintWriter out = response.getWriter();
    out.print("{\"status\":\"UP\",\"service\":\"modresorts\"}");
    out.flush();
  }
}
