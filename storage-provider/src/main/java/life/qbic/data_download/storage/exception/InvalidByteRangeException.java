package life.qbic.data_download.storage.exception;

/**
 * Thrown when a byte range is malformed or out of bounds.
 *
 * <p>Permanent failure: maps to a 416 for the client and must not be retried.
 */
public class InvalidByteRangeException extends StorageProviderException {

  public InvalidByteRangeException(String message) {
    super(message);
  }

  public InvalidByteRangeException(String message, Throwable cause) {
    super(message, cause);
  }
}