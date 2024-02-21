package life.qbic.data_access.openbis;

import java.io.InputStream;
import life.qbic.data_access.measurements.api.MeasurementData;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
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
