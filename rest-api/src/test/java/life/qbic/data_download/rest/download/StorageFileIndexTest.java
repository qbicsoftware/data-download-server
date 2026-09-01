package life.qbic.data_download.rest.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.FileInfo;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StorageFileIndexTest {

  private static final FileInfo Z = new FileInfo("/z", "z", 1, null, 1, 1);
  private static final FileInfo A = new FileInfo("/a", "a", 2, null, 2, 2);
  private static final FileInfo M = new FileInfo("/m", "m", 3, null, 3, 3);

  private static final class FakeStorageProvider implements StorageProvider {

    private final List<FileInfo> files;
    private int listCalls = 0;

    FakeStorageProvider(List<FileInfo> files) {
      this.files = files;
    }

    @Override
    public List<FileInfo> listFiles(String datasetId) {
      listCalls++;
      if ("nonexistent".equals(datasetId)) {
        throw new DatasetNotFoundException(datasetId);
      }
      return files;
    }

    @Override
    public DataFile getFile(String datasetId, int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileInfo getFileMetadata(String datasetId, int index) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeProviderRegistry implements ProviderRegistry {

    private final FakeStorageProvider provider;

    FakeProviderRegistry(FakeStorageProvider provider) {
      this.provider = provider;
    }

    @Override
    public StorageProvider getProvider(String datasetId) {
      return provider;
    }
  }

  @Test
  @DisplayName("fileByIndex resolves the file at the given index from the provider's listing")
  void fileByIndexResolvesPosition() {
    FakeStorageProvider provider = new FakeStorageProvider(List.of(Z, A, M));
    StorageFileIndex index = new StorageFileIndex(new FakeProviderRegistry(provider), Duration.ofMinutes(1));

    assertEquals("/z", index.fileByIndex("ds-1", 0).get().path());
    assertEquals("/a", index.fileByIndex("ds-1", 1).get().path());
    assertEquals("/m", index.fileByIndex("ds-1", 2).get().path());
    assertTrue(index.fileByIndex("ds-1", 3).isEmpty());
    assertTrue(index.fileByIndex("ds-1", -1).isEmpty());
  }

  @Test
  @DisplayName("files are cached so the provider is not called repeatedly within the TTL")
  void filesAreCachedWithinTtl() {
    FakeStorageProvider provider = new FakeStorageProvider(List.of(A, M, Z));
    StorageFileIndex index = new StorageFileIndex(new FakeProviderRegistry(provider), Duration.ofMinutes(1));

    index.files("ds-1");
    index.files("ds-1");
    index.files("ds-1");

    assertEquals(1, provider.listCalls);
  }

  @Test
  @DisplayName("evict removes the cached entry so the next call hits the provider again")
  void evictClearsCache() {
    FakeStorageProvider provider = new FakeStorageProvider(List.of(A));
    StorageFileIndex index = new StorageFileIndex(new FakeProviderRegistry(provider), Duration.ofMinutes(1));

    index.files("ds-1");
    index.evict("ds-1");
    index.files("ds-1");

    assertEquals(2, provider.listCalls);
  }
}
