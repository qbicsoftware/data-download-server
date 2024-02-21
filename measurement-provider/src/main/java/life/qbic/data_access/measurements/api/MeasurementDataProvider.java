package life.qbic.data_access.measurements.api;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
public interface MeasurementDataProvider {

  MeasurementData loadData(MeasurementId measurementId);

}
