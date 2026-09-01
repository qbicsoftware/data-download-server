package life.qbic.data_download.storage;

import life.qbic.data_download.storage.exception.ProviderException;

/**
 * Resolves which {@link StorageProvider} handles a given dataset.
 *
 * <p>Implementations map a dataset id to a configured provider id, then return the provider
 * instance for that id.
 */
public interface ProviderRegistry {

  /**
   * Returns the {@link StorageProvider} responsible for the given dataset.
   *
   * @param datasetId the id of the dataset
   * @return the storage provider for the dataset
   * @throws ProviderException if no provider is configured for the dataset
   */
  StorageProvider getProvider(String datasetId);
}