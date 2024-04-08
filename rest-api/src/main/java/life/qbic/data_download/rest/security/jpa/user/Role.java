package life.qbic.data_download.rest.security.jpa.user;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.security.core.GrantedAuthority;

@Entity
@Table(name = "roles")
public class Role implements GrantedAuthority {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  @ReadOnlyProperty
  private long id;

  @Column(name = "name")
  @ReadOnlyProperty
  private String name;

  @Column(name = "description")
  @ReadOnlyProperty
  private String description;


  protected Role() {
  }

  protected Role(long id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
  }

  public String name() {
    requireNonNull(this.name);
    return name;
  }

  public Optional<String> description() {
    return Optional.ofNullable(description);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", Role.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("description='" + description + "'")
        .toString();
  }

  @Override
  public String getAuthority() {
    return "ROLE_" + name();
  }

  public long getId() {
    return id;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else {
      return obj instanceof GrantedAuthority && this.getAuthority()
          .equals(((GrantedAuthority) obj).getAuthority());
    }
  }

  @Override
  public int hashCode() {
    return getAuthority().hashCode();
  }
}
