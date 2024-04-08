package life.qbic.data_download.rest.security;

import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Creates {@link AuthorizationManager<RequestAuthorizationContext>} evaluating SpEl expressions.
 */
public class RequestAuthorizationManagerFactory {

  private final SecurityExpressionHandler<RequestAuthorizationContext> securityExpressionHandler;

  public RequestAuthorizationManagerFactory(
      SecurityExpressionHandler<RequestAuthorizationContext> securityExpressionHandler) {
    this.securityExpressionHandler = securityExpressionHandler;
  }

  public AuthorizationManager<RequestAuthorizationContext> spel(String expression) {
    WebExpressionAuthorizationManager manager = new WebExpressionAuthorizationManager(expression);
    manager.setExpressionHandler(securityExpressionHandler);
    return manager;
  }
}
