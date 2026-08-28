package life.qbic.data_download.storage.exception;

/**
 * Thrown when the referenced file (by index) does not exist in the dataset.
 *
 * <p>Permanent failure: maps to a 404 for the client and must not be retried.
 */
public class StorageFileNotFoundException extends StorageProviderException {

  private final String datasetId;
  private final int index;

  public StorageFileNotFoundException(String datasetId, int index) {
    super("File not found in dataset " + datasetId + " at index " + index);
    this.datasetId = datasetId;
    this.index = index;
  }

  public String datasetId() {
    return datasetId;
  }

  public int index() {
    return index;
  }
}