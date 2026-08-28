package life.qbic.data_download.storage.exception;

/**
 * Thrown when the referenced dataset does not exist in the storage backend.
 *
 * <p>Permanent failure: maps to a 404 for the client and must not be retried.
 */
public class DatasetNotFoundException extends StorageProviderException {

  private final String datasetId;

  public DatasetNotFoundException(String datasetId) {
    super("Dataset not found: " + datasetId);
    this.datasetId = datasetId;
  }

  public String datasetId() {
    return datasetId;
  }
}