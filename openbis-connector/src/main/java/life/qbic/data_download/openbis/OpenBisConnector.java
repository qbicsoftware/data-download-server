package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

import ch.ethz.sis.openbis.generic.asapi.v3.IApplicationServerApi;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.DataSet;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.fetchoptions.DataSetFetchOptions;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.id.DataSetPermId;
import ch.ethz.sis.openbis.generic.asapi.v3.dto.dataset.search.DataSetSearchCriteria;
import ch.ethz.sis.openbis.generic.dssapi.v3.IDataStoreServerApi;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.DataSetFile;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.download.DataSetFileDownloadOptions;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.fetchoptions.DataSetFileFetchOptions;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.id.DataSetFilePermId;
import ch.ethz.sis.openbis.generic.dssapi.v3.dto.datasetfile.search.DataSetFileSearchCriteria;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementFinder;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.measurements.api.MeasurementInfo;
import life.qbic.data_download.measurements.api.PathFormatter;
import life.qbic.data_download.openbis.SessionFactory.OpenBisSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A connector to the openBIS system.
 */
@Component("openbisConnector")
public class OpenBisConnector implements MeasurementFinder, MeasurementDataProvider {

  private final SessionFactory sessionFactory;
  private final List<IDataStoreServerApi> dataStoreServers;
  private final IApplicationServerApi applicationServer;
  private final PathFormatter pathFormatter;

  private static final String UUID_REGEX = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

  public OpenBisConnector(
      @Qualifier("openbisSessionFactory") SessionFactory sessionFactory,
      @Value("${openbis.server.application.url}") String applicationServerUrl,
      @Value("${openbis.server.datastore.urls}") List<String> dataStoreServerUrls,
      @Value("${openbis.filename.ignored-prefix}") String ignoredPrefix) {
    this.sessionFactory = requireNonNull(sessionFactory, "sessionFactory must not be null");
    if (dataStoreServerUrls.isEmpty()) {
      throw new IllegalArgumentException("At least one data_store server is required.");
    }
    this.applicationServer = ApiV3.applicationServer(
        requireNonNull(applicationServerUrl, "applicationServerUrl must not be null"));
    this.dataStoreServers = dataStoreServerUrls.stream()
        .map(ApiV3::dataStoreServer)
        .toList();
    this.pathFormatter = PathFormatter.with(List.of(ignoredPrefix, UUID_REGEX));
  }

  @Override
  public MeasurementInfo findById(MeasurementId measurementId) {
    try (var session = sessionFactory.getSession()) {
      List<DataSet> dataSets = loadDataSetsForMeasurement(session, measurementId);
      List<DataSetPermId> dataSetPermIds = dataSets.stream().map(DataSet::getPermId).toList();
      List<DataSetFile> measurementFiles = searchFilesForMeasurement(session, dataSetPermIds);
      long totalFileLength = measurementFiles.stream().mapToLong(DataSetFile::getFileLength).sum();
      return new MeasurementInfo(totalFileLength, dataSetPermIds.size());
    }
  }

  @Override
  public List<FileInfo> listFiles(MeasurementId measurementId) {
    try (var session = sessionFactory.getSession()) {
      List<DataSetPermId> dataSetPermIds = loadDataSetsForMeasurement(session, measurementId)
          .stream()
          .map(DataSet::getPermId)
          .toList();
      return searchFilesForMeasurement(session, dataSetPermIds).stream()
          .map(this::toFileInfo)
          .filter(fileInfo -> !fileInfo.path().isBlank())
          .sorted(Comparator.comparing(FileInfo::path))
          .toList();
    }
  }

  @Override
  public DataFile loadFile(MeasurementId measurementId, FileInfo fileInfo) {
    try (var session = sessionFactory.getSession()) {
      List<DataSetPermId> dataSetPermIds = loadDataSetsForMeasurement(session, measurementId)
          .stream()
          .map(DataSet::getPermId)
          .toList();
      if (dataSetPermIds.isEmpty()) {
        return null;
      }
      DataSetFile dataSetFile = searchFilesForMeasurement(session, dataSetPermIds).stream()
          .filter(file -> Objects.equals(fileInfo.path(), formatPath(file.getPath())))
          .findFirst()
          .orElse(null);
      if (dataSetFile == null) {
        return null;
      }
      InputStream inputStream = getInputStreamForFiles(session, List.of(dataSetFile.getPermId()));
      return new DataFile(toFileInfo(dataSetFile), inputStream);
    }
  }

  private FileInfo toFileInfo(DataSetFile dataSetFile) {
    long creationMillis = dataSetFile.getDataStore() != null
        ? dataSetFile.getDataStore().getRegistrationDate().toInstant().toEpochMilli() : -1;
    long lastModifiedMillis = dataSetFile.getDataStore() != null
        ? dataSetFile.getDataStore().getModificationDate().toInstant().toEpochMilli() : -1;
    return new FileInfo(formatPath(dataSetFile.getPath()),
        dataSetFile.getFileLength(),
        Integer.toUnsignedLong(dataSetFile.getChecksumCRC32()),
        creationMillis,
        lastModifiedMillis);
  }

  private String formatPath(String path) {
    return pathFormatter.format(path);
  }

  private List<DataSetFile> searchFilesForMeasurement(OpenBisSession session,
      List<DataSetPermId> dataSetPermIds) {
    return dataSetPermIds.stream()
        .flatMap(dataSetPermId -> searchFilesForDatasetPermId(session, dataSetPermId).stream())
        .filter(not(DataSetFile::isDirectory))
        .toList();
  }

  private List<DataSet> loadDataSetsForMeasurement(OpenBisSession session,
      MeasurementId measurementId) {
    DataSetSearchCriteria dataSetSearchCriteria = new DataSetSearchCriteria();
    dataSetSearchCriteria.withSample().withCode().thatEquals(measurementId.id());

    DataSetFetchOptions dataSetFetchOptions = new DataSetFetchOptions();
    dataSetFetchOptions.withChildrenUsing(dataSetFetchOptions);

    return applicationServer.searchDataSets(session.getToken(),
        dataSetSearchCriteria,
        dataSetFetchOptions).getObjects();
  }

  private List<DataSetFile> searchFilesForDatasetPermId(OpenBisSession session,
      DataSetPermId datasetId) {
    DataSetFileSearchCriteria dataSetFileSearchCriteria = new DataSetFileSearchCriteria();
    dataSetFileSearchCriteria.withDataSet().withPermId().thatEquals(datasetId.toString());
    DataSetFileFetchOptions dataSetFileFetchOptions = new DataSetFileFetchOptions();
    return dataStoreServers.stream()
        .flatMap(server ->
            server.searchFiles(session.getToken(), dataSetFileSearchCriteria,
                dataSetFileFetchOptions).getObjects().stream())
        .toList();
  }

  @Override
  public MeasurementData loadData(MeasurementId measurementId) {
    var session = sessionFactory.getSession();
    try {
      List<DataSetPermId> dataSetPermIds = loadDataSetsForMeasurement(session, measurementId)
          .stream()
          .map(DataSet::getPermId)
          .toList();
      if (dataSetPermIds.isEmpty()) {
        session.close();
        return null;
      }

      List<DataSetFilePermId> measurementFileIds = dataSetPermIds.stream()
          .flatMap(dataSetPermId -> searchFilesForDatasetPermId(session, dataSetPermId).stream())
          .map(DataSetFile::getPermId)
          .collect(Collectors.toCollection(ArrayList::new));
      InputStream inputStreamForFiles = getInputStreamForFiles(session, measurementFileIds);
      // The stream is bound to the openBIS session, so the session must stay alive until the
      // stream has been fully consumed. The session is released when the stream is closed.
      return UnspecificMeasurementData.create(
          new SessionAwareInputStream(inputStreamForFiles, session));
    } catch (RuntimeException e) {
      session.close();
      throw e;
    }
  }

  public InputStream getInputStreamForFiles(OpenBisSession session,
      List<DataSetFilePermId> dataSetFilePermIds) {
    DataSetFileDownloadOptions dataSetFileDownloadOptions = new DataSetFileDownloadOptions();
    dataSetFileDownloadOptions.setRecursive(false); //only download provided files

    List<InputStream> inputStreams = dataStoreServers.stream()
        .map(it -> it.downloadFiles(session.getToken(), dataSetFilePermIds,
            dataSetFileDownloadOptions))
        .toList();
    return new SequenceInputStream(Collections.enumeration(inputStreams));
  }
}
