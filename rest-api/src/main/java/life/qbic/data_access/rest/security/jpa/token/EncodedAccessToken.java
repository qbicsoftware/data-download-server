package life.qbic.data_access.rest.security.jpa.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * The encoded access token with information which user created it
 */
@Entity
@Table(name = "personal_access_tokens")
public class EncodedAccessToken {

  @Id
  @Column(name = "id")
  protected int id;
  @Column(name = "creationDate")
  protected Instant creationDate;
  @Column(name = "duration")
  protected Duration duration;
  @Column(name = "tokenValueEncrypted")
  protected String accessToken;
  @Column(name = "userId")
  protected String userId;

  public String getUserId() {
    return userId;
  }

  /**
   * Takes the current instant at method invocation time and compares it to the expiration date of
   * the token
   *
   * @return true if the token is not expired yet; false otherwise
   */
  public boolean isNonExpired() {
    return Instant.now().isBefore(creationDate.plus(duration));
  }

  public boolean isExpired() {
    return !isNonExpired();
  }

  public String getAccessToken() {
    return accessToken;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EncodedAccessToken that)) {
      return false;
    }

    return id == that.id && Objects.equals(creationDate, that.creationDate)
        && Objects.equals(duration, that.duration) && Objects.equals(accessToken,
        that.accessToken) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    int result = id;
    result = 31 * result + Objects.hashCode(creationDate);
    result = 31 * result + Objects.hashCode(duration);
    result = 31 * result + Objects.hashCode(accessToken);
    result = 31 * result + Objects.hashCode(userId);
    return result;
  }
}
