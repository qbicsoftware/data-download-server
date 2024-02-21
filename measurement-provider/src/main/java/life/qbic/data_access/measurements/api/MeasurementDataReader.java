package life.qbic.data_access.measurements.api;

import java.io.InputStream;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
public interface MeasurementDataReader extends AutoCloseable {

  MeasurementDataReader open(InputStream stream);

  /**
   * @return the DataFile read or null if no further datafile exists
   */
  DataFile nextDataFile();

  @Override
  void close();
}
