package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;

/**
 * A {@link StorageProvider} that can serve partial content, enabling resumable downloads via
 * byte-range requests.
 *
 * <p>Implementing this interface is optional; providers that do not implement it serve whole files
 * only. Consumers detect range support with {@code instanceof ByteRangeProvider} and fall back to
 * {@link StorageProvider#getFile(String, int)} for whole-file downloads otherwise.
 */
public interface ByteRangeProvider {

  /**
   * Streams a file by its index, honoring a byte range.
   *
   * <p>The returned stream starts at the range offset. A {@code null} range denotes the whole
   * file.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @param range     the requested byte range, or {@code null} for the whole file
   * @return the file and a stream to its content starting at the range offset
   * @throws DatasetNotFoundException        if the dataset does not exist
   * @throws StorageFileNotFoundException    if no file exists at the given index
   * @throws InvalidByteRangeException       if the range is malformed or out of bounds
   * @throws StorageProviderException        on any provider error
   */
  DataFile getFile(String datasetId, int index, ByteRange range);
}