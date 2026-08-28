package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;

/**
 * Adapts the legacy {@link MeasurementDataProvider} (backed by the openBIS DSS HTTP API) to the
 * {@link StorageProvider} contract.
 *
 * <p>The adapter keeps the proven openBIS code unchanged and only translates between the two
 * contracts. Files are addressed by their index within the stable, path-sorted order of
 * {@link #listFiles(String)}, mirroring the manifest ordering clients already rely on.
 *
 * <p>The file listing of a dataset is cached for a short {@link #cacheTtl} to limit openBIS
 * traffic. Since datasets change very seldomly, requests for several files of the same dataset
 * share one openBIS listing within the TTL. The cached order is stable within the cache lifetime,
 * so indices resolved once stay valid.
 */
public class OpenBisStorageProvider implements StorageProvider {

  private static final String CRC32_ALGORITHM = "crc32";
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

  private final MeasurementDataProvider delegate;
  private final Duration cacheTtl;
  private final Map<String, CachedFiles> cache = new ConcurrentHashMap<>();

  public OpenBisStorageProvider(MeasurementDataProvider delegate) {
    this(delegate, DEFAULT_CACHE_TTL);
  }

  public OpenBisStorageProvider(MeasurementDataProvider delegate, Duration cacheTtl) {
    this.delegate = requireNonNull(delegate, "delegate must not be null");
    this.cacheTtl = requireNonNull(cacheTtl, "cacheTtl must not be null");
    if (cacheTtl.isNegative() || cacheTtl.isZero()) {
      throw new IllegalArgumentException("cacheTtl must be positive");
    }
  }

  /**
   * The cached file listing of a dataset.
   *
   * @param createdAt when the listing was fetched from openBIS
   * @param files     the files sorted by path
   */
  private record CachedFiles(Instant createdAt, List<FileInfo> files) {

    boolean expired(Duration ttl) {
      return createdAt.plus(ttl).isBefore(Instant.now());
    }
  }

  @Override
  public List<life.qbic.data_download.storage.FileInfo> listFiles(String datasetId) {
    return sortedFiles(datasetId).stream()
        .map(this::toStorageFileInfo)
        .toList();
  }

  @Override
  public DataFile getFile(String datasetId, int index) {
    FileInfo fileInfo = resolveFileInfo(datasetId, index);
    life.qbic.data_download.measurements.api.DataFile dataFile = delegate.loadFile(
        new MeasurementId(datasetId), fileInfo);
    if (dataFile == null) {
      throw new StorageFileNotFoundException(datasetId, index);
    }
    return toStorageDataFile(dataFile);
  }

  @Override
  public life.qbic.data_download.storage.FileInfo getFileMetadata(String datasetId, int index) {
    return toStorageFileInfo(resolveFileInfo(datasetId, index));
  }

  private List<FileInfo> sortedFiles(String datasetId) {
    requireNonNull(datasetId, "datasetId must not be null");
    CachedFiles cached = cache.get(datasetId);
    if (cached != null && !cached.expired(cacheTtl)) {
      return cached.files();
    }
    List<FileInfo> files = delegate.listFiles(new MeasurementId(datasetId));
    if (files == null || files.isEmpty()) {
      throw new DatasetNotFoundException(datasetId);
    }
    List<FileInfo> sorted = files.stream()
        .sorted(Comparator.comparing(FileInfo::path))
        .toList();
    cache.put(datasetId, new CachedFiles(Instant.now(), sorted));
    return sorted;
  }

  private FileInfo resolveFileInfo(String datasetId, int index) {
    List<FileInfo> files = sortedFiles(datasetId);
    if (index < 0 || index >= files.size()) {
      throw new StorageFileNotFoundException(datasetId, index);
    }
    return files.get(index);
  }

  private DataFile toStorageDataFile(life.qbic.data_download.measurements.api.DataFile dataFile) {
    return new DataFile() {
      @Override
      public InputStream inputStream() throws IOException {
        return dataFile.inputStream();
      }

      @Override
      public life.qbic.data_download.storage.FileInfo fileInfo() {
        return toStorageFileInfo(dataFile.fileInfo());
      }
    };
  }

  private life.qbic.data_download.storage.FileInfo toStorageFileInfo(FileInfo fileInfo) {
    life.qbic.data_download.storage.FileInfo.Checksum checksum =
        new life.qbic.data_download.storage.FileInfo.Checksum(CRC32_ALGORITHM,
            Long.toUnsignedString(fileInfo.crc32()));
    return new life.qbic.data_download.storage.FileInfo(fileInfo.path(), fileInfo.fileName(),
        fileInfo.length(), checksum, fileInfo.registrationMillis(), fileInfo.lastModifiedMillis());
  }
}