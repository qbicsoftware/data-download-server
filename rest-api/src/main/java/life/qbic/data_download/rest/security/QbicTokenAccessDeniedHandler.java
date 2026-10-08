package life.qbic.data_download.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Signals an authorization failure to the client with an HTTP 403 response.
 * <p>
 * This handler complements {@link QbicTokenAuthenticationEntryPoint}: authentication problems
 * (missing, invalid or expired token) are answered with 401, whereas a valid token that does not
 * grant access to the requested resource is answered with 403.
 * <p>
 * A 403 is not an invitation to authenticate, so no {@code WWW-Authenticate} challenge is sent.
 * A plain-text body explains the failure in the same style as the other error responses.
 *
 * @since 1.0.0
 */
public class QbicTokenAccessDeniedHandler implements AccessDeniedHandler {

  private static final String DESCRIPTION =
      "You do not have permission to access this resource.";

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(DESCRIPTION);
  }
}
