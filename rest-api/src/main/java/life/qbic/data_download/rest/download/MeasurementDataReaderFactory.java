package life.qbic.data_download.rest.download;

import life.qbic.data_download.measurements.api.MeasurementDataReader;

@FunctionalInterface
public interface MeasurementDataReaderFactory {

  MeasurementDataReader getMeasurementDataReader();

}
