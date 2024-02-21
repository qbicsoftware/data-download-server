package life.qbic.data_access.rest.exceptions;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
public class MeasurementNotFoundException extends RuntimeException {

  public MeasurementNotFoundException() {
  }

  public MeasurementNotFoundException(String message) {
    super(message);
  }

  public MeasurementNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public MeasurementNotFoundException(Throwable cause) {
    super(cause);
  }

  public MeasurementNotFoundException(String message, Throwable cause, boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
