package life.qbic.data_download.openbis;

import static java.util.Objects.nonNull;

import ch.ethz.sis.openbis.generic.asapi.v3.dto.datastore.DataStore;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.download.DataSetFileDownload;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.download.DataSetFileDownloadReader;
import java.io.InputStream;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementDataReader;

/**
 * Reads openbis data streams
 */
public class DatasetFileStreamReaderImpl implements MeasurementDataReader {

  private DataSetFileDownloadReader dataSetFileDownloadReader;
  public DatasetFileStreamReaderImpl() {
    dataSetFileDownloadReader = null;
  }


  @Override
  public MeasurementDataReader open(InputStream stream) {
    if (isOpen()) {
      close();
    }
    this.dataSetFileDownloadReader = new DataSetFileDownloadReader(stream);
    return this;
  }

  private boolean isOpen() {
    return nonNull(dataSetFileDownloadReader);
  }
  private boolean notOpen() {
    return !isOpen();
  }

  @Override
  public DataFile nextDataFile() {
    if (notOpen()) {
      return null;
    }
    DataSetFileDownload fileDownload = findNextFile();
    if (fileDownload == null) {
      return null;
    }
    DataStore dataStore = fileDownload.getDataSetFile().getDataStore();
    long creationMillis = nonNull(dataStore) ? dataStore.getRegistrationDate().toInstant()
        .toEpochMilli() : -1;
    long lastModifiedMillis = nonNull(dataStore) ? dataStore.getModificationDate().toInstant()
        .toEpochMilli() : -1;
    FileInfo fileInfo = new FileInfo(fileDownload.getDataSetFile().getPath(),
        fileDownload.getDataSetFile().getFileLength(),
        Integer.toUnsignedLong(fileDownload.getDataSetFile().getChecksumCRC32()),
        creationMillis,
        lastModifiedMillis);
    return new DataFile(fileInfo, fileDownload.getInputStream());
  }

  /**
   * Only return files not directories!
   * @return the next {@link DataSetFileDownload} or null if no further file was found.
   */
  private DataSetFileDownload findNextFile() {
    DataSetFileDownload readFile = dataSetFileDownloadReader.read();
    if (readFile == null) {
      return null;
    }
    if (readFile.getDataSetFile().isDirectory()) {
      return findNextFile();
    }
    return readFile;
  }

  @Override
  public void close() {
    if (notOpen()) {
      return;
    }
    dataSetFileDownloadReader.close();
    this.dataSetFileDownloadReader = null;
  }
}
