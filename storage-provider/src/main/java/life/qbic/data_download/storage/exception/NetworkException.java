package life.qbic.data_download.storage.exception;

/**
 * Thrown when a network-level failure prevents communication with the storage backend (timeout,
 * connection reset, I/O error). Retryable.
 */
public class NetworkException extends TransientException {

  public NetworkException(String message, Throwable cause) {
    super(message, cause);
  }
}