package life.qbic.data_download.measurements.api;

import java.io.InputStream;

/**
 * Reads measurement data
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
