package life.qbic.data_download.measurements.api;

import java.util.List;

/**
 * Provides measurement data given a measurement
 */
public interface MeasurementDataProvider {

  MeasurementData loadData(MeasurementId measurementId);

  /**
   * Lists the files of a measurement ordered by their formatted path.
   * <p>
   * The returned order is deterministic and stable across invocations, as long as the underlying
   * files of the measurement do not change. Callers can rely on the index within the returned list
   * to reference a specific file.
   *
   * @param measurementId the measurement to list the files of
   * @return the files of the measurement, sorted by formatted path
   */
  List<FileInfo> listFiles(MeasurementId measurementId);

  /**
   * Loads a single file of a measurement.
   *
   * @param measurementId the measurement the file belongs to
   * @param fileInfo      identifies the file to load, as returned by {@link #listFiles}
   * @return the file with its content, or {@code null} if the measurement or file does not exist
   */
  DataFile loadFile(MeasurementId measurementId, FileInfo fileInfo);

}
