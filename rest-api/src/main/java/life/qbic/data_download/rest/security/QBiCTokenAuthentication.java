package life.qbic.data_download.rest.security;

import java.util.Collection;
import java.util.List;
import life.qbic.data_download.rest.security.jpa.user.QBiCUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * This is the object after successfully resolving a personal access token. It contains a
 * {@link QBiCUserDetails} as principal.
 */
public class QBiCTokenAuthentication implements Authentication {

  private final QBiCUserDetails principal;

  public QBiCTokenAuthentication(QBiCUserDetails principal) {
    this.principal = principal;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getDetails() {
    return principal;
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    //cannot change
  }

  @Override
  public String getName() {
    return principal.id();
  }
}
