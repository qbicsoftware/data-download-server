package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;
import static org.slf4j.LoggerFactory.getLogger;

import ch.ethz.sis.openbis.generic.asapi.v3.IApplicationServerApi;
import ch.systemsx.cisd.common.exceptions.UserFailureException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates openbis sessions
 */
@Service("openbisSessionFactory")
public class SessionFactory {

  private final String applicationServerUrl;
  private final String userName;
  private final String password;

  public SessionFactory(
      @Value("${openbis.server.application.url}") String applicationServerUrl,
      @Value("${openbis.user.name}") String userName,
      @Value("${openbis.user.password}") String password) {
    this.applicationServerUrl = applicationServerUrl;
    this.userName = userName;
    this.password = password;
  }

  public OpenBisSession getSession() {
    return new AutoCloseableOpenBisSession(userName, password, applicationServerUrl);
  }
  public OpenBisSession getSession(String token) {
    return new TokenBaseOpenBisSession(token, applicationServerUrl);
  }

  public static class OpenBisSessionException extends RuntimeException {

    public OpenBisSessionException() {
    }

    public OpenBisSessionException(String message) {
      super(message);
    }

    public OpenBisSessionException(String message, Throwable cause) {
      super(message, cause);
    }

    public OpenBisSessionException(Throwable cause) {
      super(cause);
    }

    public OpenBisSessionException(String message, Throwable cause, boolean enableSuppression,
        boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }
  }

  private static final class TokenBaseOpenBisSession implements OpenBisSession {

    private final String token;
    private final IApplicationServerApi applicationServer;


    private TokenBaseOpenBisSession(String token, String applicationServerUrl) {
      applicationServer = ApiV3.applicationServer(applicationServerUrl);
      try {
        if (!applicationServer.isSessionActive(token)) {
          throw new IllegalArgumentException("Session no longer active.");
        }
      } catch (UserFailureException userFailureException) {
        throw new IllegalArgumentException(userFailureException.getMessage());
      }
      this.token = token;
    }

    @Override
    public String getToken() {
      return token;
    }

    @Override
    public void close() {
      applicationServer.logout(token);
    }
  }

  private static final class AutoCloseableOpenBisSession implements OpenBisSession, Refreshable {

    private static final Logger log = getLogger(AutoCloseableOpenBisSession.class);

    private String token;
    private final IApplicationServerApi apiV3;
    private final String userName;
    private final String password;

    private AutoCloseableOpenBisSession(String userName, String password, String applicationServerApiUrl) {
      requireNonNull(applicationServerApiUrl, "applicationServerApiUrl must not be null");
      apiV3 = ApiV3.applicationServer(applicationServerApiUrl);
      this.password = password;
      this.userName = userName;
      login();
    }


    @Override
    public void refresh() {
      logout();
      login();
    }

    private void login() throws OpenBisSessionException {
      String sessionToken = apiV3.login(userName, password);
      if (sessionToken == null) {
        throw new OpenBisSessionException("No session token generated.");
      }
      token = sessionToken;
      log.debug("Successfully logged in");
    }

    private void logout() {
      apiV3.logout(token);
      token = null;
      log.debug("Successfully logged out");
    }

    public String getToken() {
      return token;
    }

    @Override
    public void close() {
      logout();
    }
  }

  public interface OpenBisSession extends AutoCloseable {

    String getToken();

    @Override
    void close();
  }

  public interface Refreshable {
    void refresh();
  }

}
