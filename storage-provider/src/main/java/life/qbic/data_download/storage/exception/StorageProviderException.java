package life.qbic.data_download.storage.exception;

/**
 * Base class for all errors raised by a {@link life.qbic.data_download.storage.StorageProvider}.
 *
 * <p>This is an <em>unchecked</em> exception. Interface methods declare which of its subtypes they
 * can throw in their signature for documentation purposes, but callers are not forced to handle
 * them explicitly.
 *
 * <p>Only {@link TransientException} subclasses are considered retryable. All other subtypes are
 * permanent and must not be retried blindly.
 */
public class StorageProviderException extends RuntimeException {

  public StorageProviderException(String message) {
    super(message);
  }

  public StorageProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}