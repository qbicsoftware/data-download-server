package life.qbic.data_download.rest.exceptions;

/**
 * Thrown when a measurement was not found
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
