package life.qbic.data_download.storage;

import java.nio.file.Path;
import java.util.Optional;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;

/**
 * A {@link StorageProvider} backed by a locally accessible filesystem that can expose the native
 * path of a file for direct NIO operations.
 *
 * <p>Implemented only by filesystem-backed providers (NFS, local mount). Consumers use the path to
 * bypass the stream abstraction when NIO is preferable.
 */
public interface FilePathProvider {

  /**
   * Resolves the native filesystem path of a file by its index, if available.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @return the file's path, or empty if it cannot be resolved
   * @throws DatasetNotFoundException        if the dataset does not exist
   * @throws StorageFileNotFoundException    if no file exists at the given index
   * @throws StorageProviderException        on any provider error
   */
  Optional<Path> getFilePath(String datasetId, int index)
      throws DatasetNotFoundException, StorageFileNotFoundException, StorageProviderException;
}