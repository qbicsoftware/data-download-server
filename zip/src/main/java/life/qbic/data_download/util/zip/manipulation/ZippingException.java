package life.qbic.data_download.util.zip.manipulation;

/**
 * Zipping a file or directory failed.
 */
public class ZippingException extends RuntimeException {

  protected ZippingException() {
  }

  public ZippingException(String message) {
    super(message);
  }

  public ZippingException(String message, Throwable cause) {
    super(message, cause);
  }

  protected ZippingException(Throwable cause) {
    super(cause);
  }

  protected ZippingException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
