package life.qbic.data_access.rest.security;

import java.util.Collections;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * A TokenAuthenticationRequest this is the Authentication object representing the token. It is
 * intended to be used to create an authentication object representing the actual authentication in
 * a {@link org.springframework.security.authentication.AuthenticationProvider}.
 */
public class QBiCTokenAuthenticationRequest extends AbstractAuthenticationToken {

  private final String token;

  public QBiCTokenAuthenticationRequest(String token) {
    super(Collections.emptyList());
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("token cannot be empty");
    }
    this.token = token;
  }

  @Override
  public Object getCredentials() {
    return getToken();
  }

  @Override
  public Object getPrincipal() {
    return getToken();
  }

  /**
   * Get a QBiC authentication token
   *
   * @return the token that proves the caller's authority to perform the
   * {@link jakarta.servlet.http.HttpServletRequest}
   */
  public String getToken() {
    return token;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QBiCTokenAuthenticationRequest that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    return Objects.equals(token, that.token);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(token);
    return result;
  }
}
