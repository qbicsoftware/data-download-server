package life.qbic.data_download.rest.security.acl;


import java.util.Optional;

public interface MeasurementMappingService {

  /**
   * Retrieves the project id from a measurement id
   * @param measurementId the measurement for which to retrieve the project id
   * @return the project id
   */
  Optional<String> projectIdForMeasurement(String measurementId);

}
