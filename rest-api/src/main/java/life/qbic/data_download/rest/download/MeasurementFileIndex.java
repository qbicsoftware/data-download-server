package life.qbic.data_download.rest.download;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Provides the ordered file list of a measurement with a short-lived cache.
 * <p>
 * The cached order is stable within the cache lifetime, so clients can rely on the manifest index
 * to reference a specific file between subsequent requests.
 */
@Component
public class MeasurementFileIndex {

  private static final Comparator<FileInfo> FILE_SORTING = Comparator.comparing(FileInfo::path);


  private record CacheEntry(Instant createdAt, List<FileInfo> files) {

    boolean expired(Duration ttl) {
      return createdAt.plus(ttl).isBefore(Instant.now());
    }
  }

  private final MeasurementDataProvider measurementDataProvider;
  private final Duration cacheTtl;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public MeasurementFileIndex(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      @org.springframework.beans.factory.annotation.Value("${server.manifest.cache-ttl:30s}") Duration cacheTtl) {
    this.measurementDataProvider = measurementDataProvider;
    this.cacheTtl = cacheTtl;
  }

  /**
   * Returns the ordered files of a measurement.
   *
   * @param measurementId the measurement
   * @return the files in stable order, or an empty list if the measurement does not exist
   */
  public List<FileInfo> files(MeasurementId measurementId) {
    String key = measurementId.id();
    CacheEntry entry = cache.get(key);
    if (entry != null && !entry.expired(cacheTtl)) {
      return entry.files();
    }
    List<FileInfo> sortedFiles = measurementDataProvider.listFiles(measurementId)
        .stream().sorted(FILE_SORTING)
        .toList();
    cache.put(key, new CacheEntry(Instant.now(), sortedFiles));
    return sortedFiles;
  }

  /**
   * Resolves a file by its index within the ordered list.
   *
   * @param measurementId the measurement
   * @param index         the zero-based index of the file
   * @return the file at the given index, or empty if out of bounds
   */
  public Optional<FileInfo> fileByIndex(MeasurementId measurementId, int index) {
    List<FileInfo> files = files(measurementId);
    if (index < 0 || index >= files.size()) {
      return Optional.empty();
    }
    return Optional.of(files.get(index));
  }
}
