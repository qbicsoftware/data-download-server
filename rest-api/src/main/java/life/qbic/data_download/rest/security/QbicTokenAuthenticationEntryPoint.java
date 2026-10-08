package life.qbic.data_download.rest.security;

import static java.util.Objects.requireNonNull;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Signals a failed authentication to the client using an HTTP 401 response together with a
 * {@code WWW-Authenticate} challenge for the configured token scheme (RFC 6750).
 * <p>
 * This entry point distinguishes the failure modes that can occur for personal access tokens:
 * <ul>
 *   <li>{@link CredentialsExpiredException} - the token expired,</li>
 *   <li>{@link BadCredentialsException} - the token is unknown or malformed,</li>
 *   <li>any other {@link AuthenticationException} that is not caused by credentials supplied in
 *   the request (for example an anonymous request) - authentication is required.</li>
 * </ul>
 * <p>
 * Per RFC 6750 section 3.1, a request that lacks any authentication information is challenged
 * without an {@code error} code. Requests that did carry a token which failed authentication are
 * challenged with {@code error="invalid_token"} and a human-readable {@code error_description}.
 * No {@code realm} is advertised: this service has a single protection space, so the attribute
 * would carry no information a client could act on.
 * <p>
 * A response body mirroring the challenge is written as well, so that clients which do not
 * surface response headers (for example {@code wget} and {@code curl} without {@code -v}) still
 * receive a human-readable hint about the failure.
 * <p>
 * Note that this entry point is only used for <em>authentication</em> failures. A request that
 * carries a valid token but lacks the permission to access a resource results in an HTTP 403
 * (forbidden), which is handled by {@link QbicTokenAccessDeniedHandler}.
 *
 * @since 1.0.0
 */
public class QbicTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String INVALID_TOKEN = "invalid_token";

  /**
   * RFC 6750 section 3 restricts the {@code error} and {@code error_description} attribute values
   * to the character set %x20-21 / %x23-5B / %x5D-7E. Characters outside this set (notably the
   * double quote and backslash) are replaced to keep the challenge well-formed.
   */
  private static final Pattern DISALLOWED_CHARACTERS =
      Pattern.compile("[^\\x20-\\x21\\x23-\\x5B\\x5D-\\x7E]");

  private final String tokenType;

  /**
   * @param tokenType the authorization scheme name advertised in the {@code WWW-Authenticate}
   *                  challenge, for example {@code Bearer}
   */
  public QbicTokenAuthenticationEntryPoint(String tokenType) {
    this.tokenType = requireNonNull(tokenType, "tokenType must not be null");
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge(authException));
    response.setContentType(MediaType.TEXT_PLAIN_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(body(authException));
  }

  private String challenge(AuthenticationException authException) {
    // RFC 6750 section 3.1: a request without authentication information must not carry an error
    // code. In that case the challenge is just the scheme, as e.g. Spring Security's
    // BearerTokenAuthenticationEntryPoint does when it has no parameters to add.
    if (authException instanceof InsufficientAuthenticationException) {
      return tokenType;
    }
    return "%s error=\"%s\", error_description=\"%s\"".formatted(tokenType, INVALID_TOKEN,
        sanitize(errorDescription(authException)));
  }

  private static String body(AuthenticationException authException) {
    if (authException instanceof InsufficientAuthenticationException) {
      return "Authentication is required. Provide a valid personal access token.";
    }
    return errorDescription(authException);
  }

  private static String errorDescription(AuthenticationException authException) {
    if (authException instanceof CredentialsExpiredException) {
      return "The personal access token has expired.";
    }
    if (authException instanceof BadCredentialsException) {
      return "The personal access token is invalid.";
    }
    return "The personal access token could not be verified.";
  }

  private static String sanitize(String value) {
    return DISALLOWED_CHARACTERS.matcher(value).replaceAll("'");
  }
}
