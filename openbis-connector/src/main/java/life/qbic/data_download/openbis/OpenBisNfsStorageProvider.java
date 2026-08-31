package life.qbic.data_download.openbis;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.ByteRange;
import life.qbic.data_download.storage.ByteRangeProvider;
import life.qbic.data_download.storage.DataFile;
import life.qbic.data_download.storage.FilePathProvider;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;

/**
 * A hybrid storage provider that uses openBIS for metadata (file list, order, checksums, timestamps)
 * but streams file content directly from the mounted filesystem via NIO.
 *
 * <p>This provider combines the metadata richness of openBIS with the performance of direct NFS
 * access. It resolves each file's physical location by combining the configured {@code mount-path}
 * with the relative path reported by openBIS, then streams the file using Java NIO.
 *
 * <p>Implements {@link StorageProvider}, {@link ByteRangeProvider} (for resumable downloads), and
 * {@link FilePathProvider} (for direct NIO operations when needed).
 *
 * <p>The file listing is cached for a short {@link #cacheTtl} to limit openBIS traffic.
 */
public class OpenBisNfsStorageProvider implements StorageProvider, ByteRangeProvider, FilePathProvider {

  private static final String CRC32_ALGORITHM = "crc32";
  private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

  private final MeasurementDataProvider delegate;
  private final Path mountPath;
  private final Duration cacheTtl;
  private final Map<String, CachedFiles> cache = new ConcurrentHashMap<>();

  public OpenBisNfsStorageProvider(MeasurementDataProvider delegate, Path mountPath) {
    this(delegate, mountPath, DEFAULT_CACHE_TTL);
  }

  public OpenBisNfsStorageProvider(MeasurementDataProvider delegate, Path mountPath, Duration cacheTtl) {
    this.delegate = requireNonNull(delegate, "delegate must not be null");
    this.mountPath = requireNonNull(mountPath, "mountPath must not be null");
    this.cacheTtl = requireNonNull(cacheTtl, "cacheTtl must not be null");
    if (cacheTtl.isNegative() || cacheTtl.isZero()) {
      throw new IllegalArgumentException("cacheTtl must be positive");
    }
    if (!Files.isDirectory(mountPath)) {
      throw new IllegalArgumentException("mountPath must be a directory: " + mountPath);
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
    FileInfo legacyFileInfo = resolveFileInfo(datasetId, index);
    Path filePath = resolvePhysicalPath(legacyFileInfo);
    life.qbic.data_download.storage.FileInfo storageFileInfo = toStorageFileInfo(legacyFileInfo);
    return createDataFile(storageFileInfo, filePath, null);
  }

  @Override
  public DataFile getFile(String datasetId, int index, ByteRange range) {
    FileInfo legacyFileInfo = resolveFileInfo(datasetId, index);
    Path filePath = resolvePhysicalPath(legacyFileInfo);
    life.qbic.data_download.storage.FileInfo storageFileInfo = toStorageFileInfo(legacyFileInfo);
    
    if (range == null) {
      return createDataFile(storageFileInfo, filePath, null);
    }
    
    ByteRange.ResolvedRange resolved = range.resolve(storageFileInfo.size());
    return createDataFile(storageFileInfo, filePath, resolved);
  }

  @Override
  public Optional<Path> getFilePath(String datasetId, int index) {
    FileInfo legacyFileInfo = resolveFileInfo(datasetId, index);
    return Optional.of(resolvePhysicalPath(legacyFileInfo));
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

  /**
   * Resolves the physical filesystem path for a file by combining the mount-path with the file's
   * relative path from openBIS.
   */
  private Path resolvePhysicalPath(FileInfo fileInfo) {
    // The fileInfo.path() is the relative path within the dataset (e.g., "Fastq1/read1.fastq.gz")
    // We combine it with the mount-path to get the absolute path
    Path relativePath = Path.of(fileInfo.path());
    Path absolutePath = mountPath.resolve(relativePath);
    
    if (!Files.exists(absolutePath)) {
      throw new StorageProviderException(
          "File not found on filesystem: " + absolutePath);
    }
    return absolutePath;
  }

  /**
   * Creates a DataFile that streams from the given path using NIO.
   *
   * @param fileInfo the file metadata
   * @param filePath the absolute path to the file
   * @param range    the byte range to stream, or null for the whole file
   */
  private DataFile createDataFile(life.qbic.data_download.storage.FileInfo fileInfo,
      Path filePath, ByteRange.ResolvedRange range) {
    return new DataFile() {
      @Override
      public InputStream inputStream() throws IOException {
        FileChannel channel = FileChannel.open(filePath, StandardOpenOption.READ);
        if (range != null) {
          channel.position(range.start());
        }
        // Wrap the channel in a stream that closes it when done
        return new NioFileInputStream(channel, range != null ? range.length() : fileInfo.size());
      }

      @Override
      public life.qbic.data_download.storage.FileInfo fileInfo() {
        return fileInfo;
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
   * An InputStream that reads from a FileChannel and closes it when done. Supports reading a
   * limited number of bytes for byte-range requests.
   */
  private static final class NioFileInputStream extends InputStream {
    private final FileChannel channel;
    private final long limit;
    private long bytesRead = 0;

    NioFileInputStream(FileChannel channel, long limit) {
      this.channel = channel;
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      if (bytesRead >= limit) {
        return -1;
      }
      var buffer = java.nio.ByteBuffer.allocate(1);
      int read = channel.read(buffer);
      if (read <= 0) {
        return -1;
      }
      bytesRead++;
      buffer.flip();
      return buffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (bytesRead >= limit) {
        return -1;
      }
      int toRead = (int) Math.min(len, limit - bytesRead);
      var buffer = java.nio.ByteBuffer.wrap(b, off, toRead);
      int read = channel.read(buffer);
      if (read > 0) {
        bytesRead += read;
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }
}
