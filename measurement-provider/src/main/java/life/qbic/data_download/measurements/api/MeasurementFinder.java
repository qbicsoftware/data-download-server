package life.qbic.data_download.measurements.api;

public interface MeasurementFinder {
  MeasurementInfo findById(MeasurementId measurementId);
}
