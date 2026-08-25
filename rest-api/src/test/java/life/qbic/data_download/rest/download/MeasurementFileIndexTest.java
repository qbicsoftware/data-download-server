package life.qbic.data_download.rest.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeasurementFileIndexTest {

  private static final FileInfo Z = new FileInfo("/z", "z", 1, 1, 1, 1);
  private static final FileInfo A = new FileInfo("/a", "a", 2, 2, 2, 2);
  private static final FileInfo M = new FileInfo("/m", "m", 3, 3, 3, 3);

  private static final class FakeProvider implements MeasurementDataProvider {

    private final List<FileInfo> files;
    private int calls = 0;

    FakeProvider(List<FileInfo> files) {
      this.files = files;
    }

    @Override
    public MeasurementData loadData(MeasurementId measurementId) {
      return null;
    }

    @Override
    public List<FileInfo> listFiles(MeasurementId measurementId) {
      calls++;
      return files;
    }

    @Override
    public DataFile loadFile(MeasurementId measurementId, FileInfo fileInfo) {
      return null;
    }
  }

  @Test
  @DisplayName("fileByIndex resolves the file at the given sorted index alphabetically")
  void fileByIndexResolvesSortedPosition() {
    FakeProvider provider = new FakeProvider(List.of(Z, A, M));
    MeasurementFileIndex index = new MeasurementFileIndex(provider, Duration.ofMinutes(1));
    MeasurementId id = new MeasurementId("measurement-1");

    // provider returns unsorted; index resolution is by list position (provider already sorted)
    assertTrue(index.fileByIndex(id, 0).isPresent());
    assertEquals("/z", index.fileByIndex(id, 2).get().path());
    assertEquals("/a", index.fileByIndex(id, 0).get().path());
    assertEquals("/m", index.fileByIndex(id, 1).get().path());
    assertTrue(index.fileByIndex(id, 3).isEmpty());
    assertTrue(index.fileByIndex(id, -1).isEmpty());
  }

  @Test
  @DisplayName("files are cached so the provider is not called repeatedly within the TTL")
  void filesAreCachedWithinTtl() {
    FakeProvider provider = new FakeProvider(List.of(A, M, Z));
    MeasurementFileIndex index = new MeasurementFileIndex(provider, Duration.ofMinutes(1));
    MeasurementId id = new MeasurementId("measurement-1");

    index.files(id);
    index.files(id);
    index.files(id);

    assertEquals(1, provider.calls);
  }

  @Test
  @DisplayName("a null provider result is treated as an empty file list")
  void nullProviderResultIsEmpty() {
    MeasurementDataProvider nullProvider = new FakeProvider(null);
    MeasurementFileIndex index = new MeasurementFileIndex(nullProvider, Duration.ofMinutes(1));
    assertTrue(index.files(new MeasurementId("measurement-1")).isEmpty());
  }
}
