package life.qbic.data_download.rest.security.acl;

import static java.util.function.Predicate.not;

import java.util.Optional;
import life.qbic.data_download.rest.security.jpa.measurement.NGSMeasurementProject;
import life.qbic.data_download.rest.security.jpa.measurement.NGSMeasurementRepository;
import life.qbic.data_download.rest.security.jpa.measurement.ProteomicsMeasurementProject;
import life.qbic.data_download.rest.security.jpa.measurement.ProteomicsMeasurementRepository;

public class QBiCMeasurementMappingService implements MeasurementMappingService{

  private final NGSMeasurementRepository ngsMeasurementRepository;
  private final ProteomicsMeasurementRepository proteomicsMeasurementRepository;

  public QBiCMeasurementMappingService(NGSMeasurementRepository ngsMeasurementRepository,
      ProteomicsMeasurementRepository proteomicsMeasurementRepository) {
    this.ngsMeasurementRepository = ngsMeasurementRepository;
    this.proteomicsMeasurementRepository = proteomicsMeasurementRepository;
  }

  @Override
  public Optional<String> projectIdForMeasurement(String measurementId) {
    if (ngsMeasurementRepository.existsByMeasurementCode(measurementId)) {
      return ngsMeasurementRepository.findByMeasurementCode(measurementId)
          .map(NGSMeasurementProject::getProjectId)
          .filter(not(String::isBlank));
    }
    if (proteomicsMeasurementRepository.existsByMeasurementCode(measurementId)) {
      return proteomicsMeasurementRepository.findByMeasurementCode(measurementId)
          .map(ProteomicsMeasurementProject::getProjectId)
          .filter(not(String::isBlank));
    }
    return Optional.empty();
  }
}
