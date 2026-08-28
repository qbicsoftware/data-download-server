package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.ByteRangeProvider;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;

/**
 * Adapts the legacy {@link MeasurementDataProvider} (backed by the openBIS DSS HTTP API) to the
 * {@link StorageProvider} contract.
 *
 * <p>The adapter keeps the proven openBIS code unchanged and only translates between the two
 * contracts. Files are addressed by their index within the stable, path-sorted order of
 * {@link #listFiles(String)}, mirroring the manifest ordering clients already rely on.
 *
 * <p>Implements {@link ByteRangeProvider} to preserve resumable downloads: a byte range is honored
 * by skipping to the requested offset on the underlying DSS stream.
 */
public class OpenBisStorageProvider implements StorageProvider, ByteRangeProvider {

  private static final String CRC32_ALGORITHM = "crc32";

  private final MeasurementDataProvider delegate;

  public OpenBisStorageProvider(MeasurementDataProvider delegate) {
    this.delegate = requireNonNull(delegate, "delegate must not be null");
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

  @Override
  public DataFile getFile(String datasetId, int index, ByteRange range) {
    if (range == null) {
      return getFile(datasetId, index);
    }
    FileInfo fileInfo = resolveFileInfo(datasetId, index);
    if (range.start() >= fileInfo.length()) {
      throw new InvalidByteRangeException("range start out of bounds for file at index " + index);
    }
    life.qbic.data_download.measurements.api.DataFile dataFile = delegate.loadFile(
        new MeasurementId(datasetId), fileInfo);
    if (dataFile == null) {
      throw new StorageFileNotFoundException(datasetId, index);
    }
    return toStorageDataFile(dataFile, range);
  }

  private List<FileInfo> sortedFiles(String datasetId) {
    requireNonNull(datasetId, "datasetId must not be null");
    List<FileInfo> files = delegate.listFiles(new MeasurementId(datasetId));
    if (files == null || files.isEmpty()) {
      throw new DatasetNotFoundException(datasetId);
    }
    return files.stream()
        .sorted(Comparator.comparing(FileInfo::path))
        .toList();
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

  private DataFile toStorageDataFile(life.qbic.data_download.measurements.api.DataFile dataFile,
      ByteRange range) {
    return new DataFile() {
      @Override
      public InputStream inputStream() throws IOException {
        InputStream stream = dataFile.inputStream();
        skipToStart(stream, range.start());
        return stream;
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

  /**
   * Advances the stream to the requested start offset. {@link InputStream#skip} is not guaranteed
   * to skip the requested number of bytes, so we loop until the offset is reached. When skip makes
   * no progress, fall back to reading a single byte at a time so we never loop forever.
   */
  private static void skipToStart(InputStream inputStream, long start) throws IOException {
    long skipped = 0;
    while (skipped < start) {
      long skippedNow = inputStream.skip(start - skipped);
      if (skippedNow <= 0) {
        if (inputStream.read() == -1) {
          break;
        }
        skipped++;
      } else {
        skipped += skippedNow;
      }
    }
  }
}