package life.qbic.data_download.storage;

import java.util.List;
import life.qbic.data_download.storage.exception.DatasetNotFoundException;
import life.qbic.data_download.storage.exception.StorageFileNotFoundException;
import life.qbic.data_download.storage.exception.StorageProviderException;

/**
 * Abstraction over a storage backend, exposing the files of a dataset through a lean, uniform
 * contract.
 *
 * <p>Files are addressed by their index within the stable, deterministic order returned by
 * {@link #listFiles(String)}. Callers can rely on that order being consistent for a given dataset
 * (e.g. via a cached manifest), so an index resolved from a manifest stays valid for subsequent
 * {@link #getFile(String, int)} calls.
 *
 * <p>Providers may implement additional capability interfaces (such as {@link ByteRangeProvider})
 * for behavior not every backend supports; consumers detect those via pattern matching.
 */
public interface StorageProvider {

  /**
   * Lists all files of a dataset in stable, deterministic order.
   *
   * @param datasetId the id of the dataset
   * @return the files of the dataset, in a stable order
   * @throws DatasetNotFoundException   if the dataset does not exist
   * @throws StorageProviderException   on any provider error
   */
  List<FileInfo> listFiles(String datasetId) throws DatasetNotFoundException, StorageProviderException;

  /**
   * Streams a file by its index within the ordered list returned by {@link #listFiles(String)}.
   * The whole file is streamed from the start.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @return the file and a stream to its content
   * @throws DatasetNotFoundException        if the dataset does not exist
   * @throws StorageFileNotFoundException    if no file exists at the given index
   * @throws StorageProviderException        on any provider error
   */
  DataFile getFile(String datasetId, int index)
      throws DatasetNotFoundException, StorageFileNotFoundException, StorageProviderException;

  /**
   * Returns the metadata of a file by its index.
   *
   * @param datasetId the id of the dataset
   * @param index     the zero-based index of the file
   * @return the file metadata
   * @throws DatasetNotFoundException        if the dataset does not exist
   * @throws StorageFileNotFoundException    if no file exists at the given index
   * @throws StorageProviderException        on any provider error
   */
  FileInfo getFileMetadata(String datasetId, int index)
      throws DatasetNotFoundException, StorageFileNotFoundException, StorageProviderException;
}