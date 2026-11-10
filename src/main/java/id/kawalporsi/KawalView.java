package id.kawalporsi;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.HttpSession;

@Route("")
@PageTitle("KawalPorsi · Transparansi Dapur MBG")
public final class KawalView extends Div implements BeforeEnterObserver {
  private final Application app;
  private com.vaadin.flow.shared.Registration polling;
  private long lastAlert;

  private long latestAlert(Service.Session session) {
    return app.service.db.transaction(
        c -> {
          var u = Service.user(c, session);
          if (!u.str("role").equals("AUDITOR")) return 0L;
          var permitted = java.util.Arrays.asList(u.str("regions").split("\\|"));
          long last = 0;
          for (var r :
              Database.rows(
                  c,
                  "select k.region,max(a.seq) n from alert a join purchase t on t.id=a.purchase"
                      + " join kitchen k on k.id=t.kitchen group by k.region"))
            if (permitted.contains(r.str("region"))) last = Math.max(last, r.num("n"));
          return last;
        });
  }

  @Override
  protected void onDetach(com.vaadin.flow.component.DetachEvent event) {
    if (polling != null) polling.remove();
    super.onDetach(event);
  }

  public KawalView(Application app) {
    this.app = app;
    setWidthFull();
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    var request = (VaadinServletRequest) VaadinService.getCurrentRequest();
    HttpSession http = request.getHttpServletRequest().getSession();
    var session = WorkflowServlet.session(http);
    var p = new Web.Params();
    event
        .getLocation()
        .getQueryParameters()
        .getParameters()
        .forEach((k, vs) -> vs.forEach(v -> p.add(k, v)));
    String content;
    synchronized (session) {
      try {
        content = app.fragment(session, p);
      } catch (SecurityException | IllegalArgumentException ex) {
        content = Application.errorBody(ex.getMessage());
      }
    }
    if (polling != null) {
      polling.remove();
      polling = null;
    }
    var ui = UI.getCurrent();
    boolean auditor =
        app.service.db.transaction(c -> Service.user(c, session).str("role").equals("AUDITOR"));
    ui.setPollInterval(auditor ? 4000 : -1);
    if (auditor) {
      lastAlert = latestAlert(session);
      polling =
          ui.addPollListener(
              e -> {
                long latest = latestAlert(session);
                if (latest > lastAlert) {
                  lastAlert = latest;
                  getElement()
                      .executeJs(
                          "const"
                              + " el=this.querySelector('[data-alert-update]');if(el){el.hidden=false;el.textContent='Ada"
                              + " pemberitahuan baru. Buka notifikasi untuk melihat transaksi.';}");
                }
              });
    }
    getElement().setProperty("innerHTML", content);
    var page = UI.getCurrent().getPage();
    page.addStyleSheet("/styles.css");
    page.addJavaScript("/ui.js");
  }
}
