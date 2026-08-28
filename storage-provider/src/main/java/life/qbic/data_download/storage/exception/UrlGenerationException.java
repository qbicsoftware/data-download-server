package life.qbic.data_download.storage.exception;

/**
 * Thrown when a pre-signed URL cannot be generated, even though the provider supports them.
 *
 * <p>This is distinct from a provider simply not supporting pre-signed URLs (which is expressed by
 * not implementing {@code PresignedUrlProvider}); it signals a failure during generation that may
 * or may not be transient.
 */
public class UrlGenerationException extends StorageProviderException {

  public UrlGenerationException(String message) {
    super(message);
  }

  public UrlGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}