package life.qbic.data_download.storage;

import java.util.Optional;

/**
 * Resolves a dataset id to the id of the {@link StorageProvider} that should serve it.
 *
 * <p>Implementations back this mapping however they see fit (initially a database query); the
 * {@link ProviderRegistry} consults this to pick the provider for a dataset.
 */
public interface DatasetProviderResolver {

  /**
   * Returns the provider id that serves the given dataset, if any.
   *
   * @param datasetId the id of the dataset
   * @return the provider id serving the dataset, or empty if none is mapped
   */
  Optional<String> providerIdFor(String datasetId);
}