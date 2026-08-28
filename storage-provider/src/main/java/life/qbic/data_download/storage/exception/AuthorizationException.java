package life.qbic.data_download.storage.exception;

/**
 * Thrown when the caller is not entitled to access the requested dataset.
 *
 * <p>Permanent failure: maps to a 403 for the client and must not be retried.
 */
public class AuthorizationException extends StorageProviderException {

  public AuthorizationException(String message) {
    super(message);
  }
}