package life.qbic.data_download.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * A temporary URL that grants direct access to a file, issued by a {@link PresignedUrlProvider}.
 *
 * @param url        the temporary URL
 * @param expiresAt  when the URL stops being valid
 */
public record PresignedUrl(URI url, Instant expiresAt) {

  public PresignedUrl {
    Objects.requireNonNull(url, "url must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }
}