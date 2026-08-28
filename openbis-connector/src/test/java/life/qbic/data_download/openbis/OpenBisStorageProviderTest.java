package life.qbic.data_download.openbis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenBisStorageProviderTest {

  private final FakeMeasurementProvider fake = new FakeMeasurementProvider();
  private final StorageProvider provider = new OpenBisStorageProvider(fake);

  @Test
  @DisplayName("listFiles returns files sorted by path")
  void listFilesSortsByPath() {
    List<life.qbic.data_download.storage.FileInfo> files = provider.listFiles("M-1");
    assertEquals(List.of("/a", "/b", "/m", "/z"), files.stream()
        .map(life.qbic.data_download.storage.FileInfo::path).toList());
  }

  @Test
  @DisplayName("listFiles maps size and checksum from the legacy FileInfo")
  void listFilesMapsMetadata() {
    life.qbic.data_download.storage.FileInfo file = provider.listFiles("M-1").get(0);
    assertEquals(10, file.size());
    assertEquals("crc32", file.checksum().algorithm());
    assertEquals("123456789", file.checksum().value());
  }

  @Test
  @DisplayName("listFiles throws DatasetNotFoundException for an unknown dataset")
  void listFilesThrowsForUnknownDataset() {
    assertThrows(DatasetNotFoundException.class, () -> provider.listFiles("unknown"));
  }

  @Test
  @DisplayName("getFile streams the whole file for the resolved index")
  void getFileStreamsWholeFile() throws IOException {
    byte[] content = readAll(provider.getFile("M-1", 0).inputStream());
    assertArrayEquals("/a content".getBytes(), content);
  }

  @Test
  @DisplayName("getFile throws StorageFileNotFoundException for an out-of-bounds index")
  void getFileThrowsForOutOfBoundsIndex() {
    assertThrows(StorageFileNotFoundException.class, () -> provider.getFile("M-1", 99));
  }

  @Test
  @DisplayName("byte-range getFile skips to the requested offset")
  void getFileWithRangeSkipsToOffset() throws IOException {
    life.qbic.data_download.storage.ByteRangeProvider rangeProvider =
        (life.qbic.data_download.storage.ByteRangeProvider) provider;
    byte[] content = readAll(rangeProvider.getFile("M-1", 0, new ByteRange(3, 5)).inputStream());
    // "/a content" -> skipping first 3 bytes yields the remaining 7 bytes
    assertArrayEquals("content".getBytes(), content);
  }

  @Test
  @DisplayName("byte-range getFile throws InvalidByteRangeException when start is out of bounds")
  void getFileWithRangeThrowsForOutOfBounds() {
    life.qbic.data_download.storage.ByteRangeProvider rangeProvider =
        (life.qbic.data_download.storage.ByteRangeProvider) provider;
    assertThrows(InvalidByteRangeException.class,
        () -> rangeProvider.getFile("M-1", 0, new ByteRange(100, 101)));
  }

  @Test
  @DisplayName("null byte range delegates to whole-file getFile")
  void nullRangeDelegatesToWholeFile() throws IOException {
    life.qbic.data_download.storage.ByteRangeProvider rangeProvider =
        (life.qbic.data_download.storage.ByteRangeProvider) provider;
    byte[] content = readAll(rangeProvider.getFile("M-1", 0, null).inputStream());
    assertArrayEquals("/a content".getBytes(), content);
  }

  private static byte[] readAll(InputStream stream) throws IOException {
    try (stream) {
      return stream.readAllBytes();
    }
  }

  /**
   * A fake legacy provider returning files in a deliberately unsorted order to verify the adapter
   * sorts them by path and maps metadata correctly.
   */
  private static final class FakeMeasurementProvider implements MeasurementDataProvider {

    private final List<FileInfo> files = List.of(
        fileInfo("/m", 5, 5L),
        fileInfo("/z", 8, 8L),
        fileInfo("/a", 10, 123456789L),
        fileInfo("/b", 7, 7L));

    @Override
    public MeasurementData loadData(MeasurementId measurementId) {
      return () -> new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public List<FileInfo> listFiles(MeasurementId measurementId) {
      if (!"M-1".equals(measurementId.id())) {
        return List.of();
      }
      return files;
    }

    @Override
    public life.qbic.data_download.measurements.api.DataFile loadFile(MeasurementId measurementId,
        FileInfo fileInfo) {
      if (!"M-1".equals(measurementId.id())) {
        return null;
      }
      return files.stream()
          .filter(f -> f.path().equals(fileInfo.path()))
          .findFirst()
          .map(f -> new life.qbic.data_download.measurements.api.DataFile(f,
              new ByteArrayInputStream((f.path() + " content").getBytes())))
          .orElse(null);
    }

    private static FileInfo fileInfo(String path, long length, long crc32) {
      return new FileInfo(path, path.substring(1), length, crc32, 1L, 2L);
    }
  }
}