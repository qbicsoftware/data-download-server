package life.qbic.data_download.rest.download;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the ordered file list of a dataset with a short-lived cache, backed by the
 * {@link ProviderRegistry} and its resolved {@link StorageProvider}.
 *
 * <p>The cached order is stable within the cache lifetime, so clients can rely on the manifest
 * index to reference a specific file between subsequent requests.
 */
@Component
public class StorageFileIndex {

  private record CacheEntry(Instant createdAt, List<life.qbic.data_download.storage.FileInfo> files) {

    boolean expired(Duration ttl) {
      return createdAt.plus(ttl).isBefore(Instant.now());
    }
  }

  private final ProviderRegistry providerRegistry;
  private final Duration cacheTtl;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public StorageFileIndex(ProviderRegistry providerRegistry,
      @Value("${server.manifest.cache-ttl:30s}") Duration cacheTtl) {
    this.providerRegistry = providerRegistry;
    this.cacheTtl = cacheTtl;
  }

  /**
   * Returns the ordered files of a dataset.
   *
   * @param datasetId the id of the dataset
   * @return the files in stable order
   * @throws life.qbic.data_download.storage.exception.DatasetNotFoundException if the dataset does not exist
   * @throws life.qbic.data_download.storage.exception.StorageProviderException on any provider error
   */
  public List<life.qbic.data_download.storage.FileInfo> files(String datasetId) {
    CacheEntry entry = cache.get(datasetId);
    if (entry != null && !entry.expired(cacheTtl)) {
      return entry.files();
    }
    StorageProvider provider = providerRegistry.getProvider(datasetId);
    List<life.qbic.data_download.storage.FileInfo> files = provider.listFiles(datasetId);
    cache.put(datasetId, new CacheEntry(Instant.now(), files));
    return files;
  }

  /**
   * Resolves a file by its index within the ordered list.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @return the file at the given index, or empty if out of bounds
   */
  public Optional<life.qbic.data_download.storage.FileInfo> fileByIndex(String datasetId, int index) {
    List<life.qbic.data_download.storage.FileInfo> files = files(datasetId);
    if (index < 0 || index >= files.size()) {
      return Optional.empty();
    }
    return Optional.of(files.get(index));
  }

  /**
   * Invalidates the cached file listing for the given dataset.
   *
   * @param datasetId the id of the dataset
   */
  public void evict(String datasetId) {
    cache.remove(datasetId);
  }
}
