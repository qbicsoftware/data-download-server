package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.InvalidByteRangeException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;
import life.qbic.data_download.storage.exception.UrlGenerationException;

/**
 * A {@link StorageProvider} that can issue pre-signed URLs for direct client access, bypassing the
 * download server entirely.
 *
 * <p>Implemented only by cloud-backed providers (S3, Azure Blob).
 */
public interface PresignedUrlProvider {

  /**
   * Issues a temporary URL granting direct access to a file.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @param range     the requested byte range, or {@code null} for the whole file
   * @return a temporary pre-signed URL
   * @throws DatasetNotFoundException        if the dataset does not exist
   * @throws StorageFileNotFoundException    if no file exists at the given index
   * @throws InvalidByteRangeException       if the range is malformed or out of bounds
   * @throws UrlGenerationException          if the URL could not be generated
   * @throws StorageProviderException        on any provider error
   */
  PresignedUrl getPresignedUrl(String datasetId, int index, ByteRange range);
}