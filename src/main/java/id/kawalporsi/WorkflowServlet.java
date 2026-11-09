package id.kawalporsi;

import jakarta.servlet.http.*;
import java.io.IOException;

final class WorkflowServlet extends HttpServlet {
  private final Application app;

  WorkflowServlet(Application app) {
    this.app = app;
  }

  static Service.Session session(HttpSession http) {
    synchronized (http) {
      Service.Session s = (Service.Session) http.getAttribute("kp-session");
      if (s == null) {
        s = new Service.Session();
        http.setAttribute("kp-session", s);
      }
      return s;
    }
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse res) throws IOException {
    var s = session(req.getSession());
    Application.Response result;
    synchronized (s) {
      String prior = s.user;
      result =
          app.handle(
              req.getMethod(),
              req.getRequestURI(),
              req.getQueryString(),
              req.getContentType(),
              req.getInputStream(),
              s);
      if (!prior.equals(s.user)) req.changeSessionId();
    }
    res.setStatus(result.code());
    res.setContentType(result.type());
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Cache-Control", "no-store");
    result.headers().forEach(res::setHeader);
    res.getOutputStream().write(result.data());
  }
}
