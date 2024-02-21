package life.qbic.data_access.rest;

import java.util.List;
import java.util.ServiceLoader;
import life.qbic.data_access.measurements.api.MeasurementDataReader;
import life.qbic.data_access.openbis.DatasetFileStreamReaderImpl;
import life.qbic.data_access.openbis.OpenBisConnector;
import life.qbic.data_access.openbis.SessionFactory;
import org.aspectj.internal.lang.reflect.InterTypeMethodDeclarationImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
@Configuration
public class AppConfig {

  @Bean({"measurementFinder", "measurementDataProvider"})
  public OpenBisConnector openBisConnector(
      @Qualifier("openbisSessionFactory") SessionFactory sessionFactory,
      @Value("${openbis.server.application.url}") String applicationServerUrl,
      @Value("${openbis.server.datastore.urls}") List<String> dataStoreServerUrls) {
    return new OpenBisConnector(sessionFactory, applicationServerUrl, dataStoreServerUrls);
  }

  @Bean("openbisSessionFactory")
  public SessionFactory sessionFactory(
      @Value("${openbis.server.application.url}") String applicationServerUrl,
      @Value("${openbis.user.name}") String userName,
      @Value("${openbis.user.password}") String password) {
    return new SessionFactory(applicationServerUrl, userName, password);
  }

  @Bean("measurementDataReader")
  public MeasurementDataReader measurementDataReader() {
    return new DatasetFileStreamReaderImpl();
  }

  @Bean("errorMessageSource")
  public MessageSource errorMessageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setBasename("classpath:exception-messages");
    messageSource.setDefaultEncoding("UTF-8");
    return messageSource;
  }
}
