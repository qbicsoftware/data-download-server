package life.qbic.data_download.storage.exception;

/**
 * A permanent, non-retryable failure raised by the storage provider that does not fit any more
 * specific category. Must not be retried.
 */
public class ProviderException extends StorageProviderException {

  public ProviderException(String message) {
    super(message);
  }

  public ProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}