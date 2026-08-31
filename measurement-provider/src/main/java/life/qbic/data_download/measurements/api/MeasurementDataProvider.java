package life.qbic.data_download.measurements.api;

import java.util.List;
import java.util.Optional;

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

  /**
   * Returns the physical storage location of a measurement's data, if available.
   * <p>
   * This is used by NFS-based providers to resolve the actual filesystem path where files are
   * stored. The location is typically a sharded directory structure (e.g.,
   * {@code UUID/c0/0d/c3/timestamp}).
   *
   * @param measurementId the measurement to get the physical location for
   * @return the physical storage location, or empty if not available
   */
  default Optional<String> getPhysicalLocation(MeasurementId measurementId) {
    return Optional.empty();
  }

}
