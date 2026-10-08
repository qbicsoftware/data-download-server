package life.qbic.data_download.rest.security;

import static java.util.Objects.requireNonNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;


/**
 * Authenticates requests that contain a QBiC authentication token
 * <p>
 * This filter should be wired with an
 * {@link AuthenticationManager} that can authenticate a
 * {@link QBiCTokenAuthenticationRequest}
 *
 * @since 1.0.0
 */
public class QBiCTokenAuthenticationFilter extends OncePerRequestFilter {

  private final AuthenticationManager authenticationManager;
  private final String tokenHeaderName;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
  private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

  public QBiCTokenAuthenticationFilter(
      AuthenticationManager authenticationManager,
      String tokenHeaderName,
      AuthenticationEntryPoint authenticationEntryPoint) {
    this.authenticationManager = requireNonNull(authenticationManager,
        "authenticationManager must not be null");
    this.tokenHeaderName = requireNonNull(tokenHeaderName, "tokenHeaderName must not be null");
    this.authenticationEntryPoint = requireNonNull(authenticationEntryPoint,
        "authenticationEntryPoint must not be null");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain) throws ServletException, IOException {
    //extract token from request
    String authorizationHeader = request.getHeader("Authorization");
    if (authorizationHeader == null) {
      // no authorization header
      logger.trace("No Authorization header found");
      filterChain.doFilter(request, response);
      return;
    }
    if (!authorizationHeader.startsWith(tokenHeaderName + " ")
        && !authorizationHeader.equals(tokenHeaderName)) {
      //authorization header not matching expected name
      logger.trace("Authorization does not contain token of type " + tokenHeaderName);
      filterChain.doFilter(request, response);
      return;
    }

    String token = authorizationHeader.substring(tokenHeaderName.length()).stripLeading();
    if (token.isBlank()) {
      // An Authorization header with the expected scheme but no credential is a malformed
      // authentication attempt, not an anonymous request: challenge the client.
      logger.debug("Authorization header contained no token.");
      failAuthentication(request, response,
          new BadCredentialsException("no token provided"));
      return;
    }
    var authentication = new QBiCTokenAuthenticationRequest(token);
    logger.trace("Trying to authenticate token.");
    Authentication authenticatedAuthentication;
    try {
      authenticatedAuthentication = authenticationManager.authenticate(authentication);
    } catch (AuthenticationException e) {
      // Signal the failure to the client (401 + WWW-Authenticate) and stop the chain.
      // A failed authentication is not an authorization failure, so this must not fall through
      // to the 403 the access decision manager would produce for an anonymous request.
      logger.debug("Failed to authenticate token: " + e.getMessage());
      failAuthentication(request, response, e);
      return;
    }
    // We need to save the authentication to the context as described in https://github.com/spring-projects/spring-security/issues/12758#issuecomment-1443729881
    logger.trace("Successfully authenticated token.");
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authenticatedAuthentication);
    securityContextHolderStrategy.setContext(context);
    this.securityContextRepository.saveContext(context, request, response);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug("Set SecurityContextHolder to %s".formatted(authenticatedAuthentication));
    }
    filterChain.doFilter(request, response);
  }

  private void failAuthentication(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {
    securityContextHolderStrategy.clearContext();
    authenticationEntryPoint.commence(request, response, authException);
  }
}
