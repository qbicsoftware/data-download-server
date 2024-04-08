package life.qbic.data_download.rest.security.jpa.measurement;

/**
 * A measurement in the context of a project
 */
public sealed interface MeasurementProject permits NGSMeasurementProject, ProteomicsMeasurementProject {

}
