package life.qbic.data_download.measurements.api;

/**
 * Provides measurement data given a measurement
 */
public interface MeasurementDataProvider {

  MeasurementData loadData(MeasurementId measurementId);

}
