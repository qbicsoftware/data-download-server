package life.qbic.data_access.rest.security.jpa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A representation of an authenticated user.
 * This implementation only provides the most basic information necessary to authorize a user.
 */
@Entity
@Table(name = "users")
public class QBiCUserDetails implements UserDetails {

  @Id
  @ReadOnlyProperty
  @Column(name = "id")
  private String id;

  @ReadOnlyProperty
  @Column(nullable = false, name = "active")
  private boolean active;

  public String id() {
    return id;
  }

  public boolean active() {
    return active;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return id();
  }

  @Override
  public boolean isAccountNonExpired() {
    return active;
  }

  @Override
  public boolean isAccountNonLocked() {
    return active;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return active;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof QBiCUserDetails that)) {
      return false;
    }

    return active == that.active && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(id);
    result = 31 * result + Boolean.hashCode(active);
    return result;
  }
}
