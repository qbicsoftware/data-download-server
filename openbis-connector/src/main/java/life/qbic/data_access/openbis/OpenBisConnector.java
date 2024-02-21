package life.qbic.data_access.openbis;

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
import java.util.List;
import java.util.stream.Collectors;
import life.qbic.data_access.measurements.api.MeasurementData;
import life.qbic.data_access.measurements.api.MeasurementDataProvider;
import life.qbic.data_access.measurements.api.MeasurementFinder;
import life.qbic.data_access.measurements.api.MeasurementId;
import life.qbic.data_access.measurements.api.MeasurementInfo;
import life.qbic.data_access.openbis.SessionFactory.OpenBisSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TODO!
 * <b>short description</b>
 *
 * <p>detailed description</p>
 *
 * @since <version tag>
 */
@Component("openbisConnector")
public class OpenBisConnector implements MeasurementFinder, MeasurementDataProvider {

  private final SessionFactory sessionFactory;
  private final List<IDataStoreServerApi> dataStoreServers;
  private final IApplicationServerApi applicationServer;

  public OpenBisConnector(
      @Qualifier("openbisSessionFactory") SessionFactory sessionFactory,
      @Value("${openbis.server.application.url}") String applicationServerUrl,
      @Value("${openbis.server.datastore.urls}") List<String> dataStoreServerUrls) {
    this.sessionFactory = requireNonNull(sessionFactory, "sessionFactory must not be null");
    if (dataStoreServerUrls.isEmpty()) {
      throw new IllegalArgumentException("At least one data_store server is required.");
    }
    this.applicationServer = ApiV3.applicationServer(
        requireNonNull(applicationServerUrl, "applicationServerUrl must not be null"));
    this.dataStoreServers = dataStoreServerUrls.stream()
        .map(ApiV3::dataStoreServer)
        .toList();
  }

  @Override
  public MeasurementInfo findById(MeasurementId measurementId) {
    try (var session = sessionFactory.getSession()) {
      List<DataSet> dataSets = loadDataSetsForMeasurement(session, measurementId);
      List<DataSetPermId> dataSetPermIds = dataSets.stream().map(DataSet::getPermId).toList();
      List<DataSetFile> measurementFiles = dataSetPermIds.stream()
          .flatMap(dataSetPermId -> searchFilesForDatasetPermId(session, dataSetPermId).stream())
          .filter(not(DataSetFile::isDirectory))
          .toList();
      long totalFileLength = measurementFiles.stream().mapToLong(DataSetFile::getFileLength).sum();
      return new MeasurementInfo(totalFileLength, dataSetPermIds.size());
    }
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
    try (var session = sessionFactory.getSession()) {
      List<DataSetPermId> dataSetPermIds = loadDataSetsForMeasurement(session, measurementId)
          .stream()
          .map(DataSet::getPermId)
          .toList();
      if (dataSetPermIds.isEmpty()) {
        return null;
      }

      List<DataSetFilePermId> measurementFileIds = dataSetPermIds.stream()
          .flatMap(dataSetPermId -> searchFilesForDatasetPermId(session, dataSetPermId).stream())
          .map(DataSetFile::getPermId)
          .collect(Collectors.toCollection(ArrayList::new));
      InputStream inputStreamForFiles = getInputStreamForFiles(session, measurementFileIds);
      return UnspecificMeasurementData.create(inputStreamForFiles);
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
