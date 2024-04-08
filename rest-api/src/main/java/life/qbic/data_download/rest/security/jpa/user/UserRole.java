package life.qbic.data_download.rest.security.jpa.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import org.springframework.data.annotation.ReadOnlyProperty;

@Entity
@Table(name = "user_role")
public class UserRole implements Serializable {
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @ReadOnlyProperty
  private long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "userId", nullable = false)
  @ReadOnlyProperty
  private QBiCUserDetails user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "roleId")
  @ReadOnlyProperty
  private Role role;

  public Role role() {
    return role;
  }
}
