package life.qbic.data_download.rest.security;

import static java.util.Objects.requireNonNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
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

  private SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
  private SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

  public QBiCTokenAuthenticationFilter(
      AuthenticationManager authenticationManager,
      String tokenHeaderName) {
    this.authenticationManager = requireNonNull(authenticationManager,
        "authenticationManager must not be null");
    this.tokenHeaderName = requireNonNull(tokenHeaderName, "tokenHeaderName must not be null");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    //extract token from request
    String authorizationHeader = request.getHeader("Authorization");
    if (authorizationHeader == null) {
      // no authorization header
      logger.trace("No Authorization header found");
      filterChain.doFilter(request, response);
      return;
    }
    if (!authorizationHeader.startsWith(tokenHeaderName + " ")) {
      //authorization header not matching expected name
      logger.trace("Authorization does not contain token of type " + tokenHeaderName);
      filterChain.doFilter(request, response);
      return;
    }

    String token = authorizationHeader.substring(tokenHeaderName.length()).stripLeading();
    if (token.isBlank()) {
      // no token provided
      logger.trace("Token is blank");
      filterChain.doFilter(request, response);
      return;
    }
    var authentication = new QBiCTokenAuthenticationRequest(token);
    logger.trace("Trying to authenticate token " + authentication.getToken());
    Authentication authenticatedAuthentication = authenticationManager.authenticate(authentication);
    // We need to save the authentication to the context as described in https://github.com/spring-projects/spring-security/issues/12758#issuecomment-1443729881
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authenticatedAuthentication);
    securityContextHolderStrategy.setContext(context);
    this.securityContextRepository.saveContext(context, request, response);
    if (this.logger.isDebugEnabled()) {
      this.logger.debug("Set SecurityContextHolder to %s".formatted(authenticatedAuthentication));
    }
    filterChain.doFilter(request, response);
  }

  public void setSecurityContextHolderStrategy(
      SecurityContextHolderStrategy securityContextHolderStrategy) {
    requireNonNull(securityContextHolderStrategy, "securityContextHolderStrategy must not be null");
    this.securityContextHolderStrategy = securityContextHolderStrategy;
  }

  public void setSecurityContextRepository(
      SecurityContextRepository securityContextRepository) {
    requireNonNull(securityContextRepository, "securityContextRepository must not be null");
    this.securityContextRepository = securityContextRepository;
  }
}
