package life.qbic.data_download.openbis;

import java.io.InputStream;
import life.qbic.data_download.measurements.api.MeasurementData;

/**
 * A default implementation of {@link MeasurementData}. No specific type of measurement but can be
 * streamed.
 */
public class UnspecificMeasurementData implements MeasurementData {

  private InputStream inputStream;
  private UnspecificMeasurementData() {
  }

  public static UnspecificMeasurementData create(InputStream inputStream) {
    var measurementData = new UnspecificMeasurementData();
    measurementData.inputStream = inputStream;
    return measurementData;
  }

  @Override
  public InputStream stream() {
    return inputStream;
  }
}
