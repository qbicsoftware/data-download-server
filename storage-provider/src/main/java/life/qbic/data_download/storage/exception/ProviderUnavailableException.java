package life.qbic.data_download.storage.exception;

/**
 * Thrown when the storage provider itself is temporarily unavailable (e.g. an upstream 503/502, or
 * the backend is down). Retryable.
 */
public class ProviderUnavailableException extends TransientException {

  public ProviderUnavailableException(String message) {
    super(message);
  }

  public ProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}