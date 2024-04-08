package life.qbic.data_download.rest.security.jpa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
  @Column(name = "id")
  @ReadOnlyProperty
  private String id;

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "user")
  @ReadOnlyProperty
  private Set<UserRole> userRoles;

  @Column(nullable = false, name = "active")
  @ReadOnlyProperty
  private boolean active;

  public String id() {
    return id;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return userRoles.stream().map(UserRole::role).toList();
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
