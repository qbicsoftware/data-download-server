package life.qbic.data_download.storage.exception;

/**
 * Base class for errors that may succeed when retried.
 *
 * <p>This is the only branch of {@link StorageProviderException} that a retry mechanism may retry.
 * Subclasses indicate transient, recoverable conditions such as network hiccups or a temporarily
 * unavailable provider. Retrying a non-transient exception is a bug.
 */
public abstract class TransientException extends StorageProviderException {

  protected TransientException(String message) {
    super(message);
  }

  protected TransientException(String message, Throwable cause) {
    super(message, cause);
  }
}