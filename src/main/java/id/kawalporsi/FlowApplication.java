package id.kawalporsi;

import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FlowApplication implements AppShellConfigurator {
  public static void main(String[] args) {
    SpringApplication.run(FlowApplication.class, args);
  }

  @Bean
  Application kawalPorsi() {
    return new Application(System.getProperty("kawal.db", "jdbc:h2:file:./data/kawalporsi"));
  }

  @Bean
  ServletRegistrationBean<WorkflowServlet> workflowEndpoints(Application app) {
    return new ServletRegistrationBean<>(
        new WorkflowServlet(app),
        "/action",
        "/file",
        "/export",
        "/ui.js",
        "/styles.css",
        "/icons/*",
        "/fonts/*");
  }
}
