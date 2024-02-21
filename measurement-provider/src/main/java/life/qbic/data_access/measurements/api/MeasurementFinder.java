package life.qbic.data_access.measurements.api;

public interface MeasurementFinder {
  MeasurementInfo findById(MeasurementId measurementId);
}
