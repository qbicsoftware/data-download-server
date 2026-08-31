package life.qbic.data_download.storage;

/**
 * Builds a {@link StorageProvider} instance for a configured {@link ProviderDefinition}, dispatching
 * on the definition's {@code type}.
 *
 * <p>A factory implementation knows the concrete provider classes of the types it supports and
 * throws an exception for an unknown or unsupported type.
 */
@FunctionalInterface
public interface ProviderFactory {

  /**
   * Creates a {@link StorageProvider} for the given definition.
   *
   * @param definition the configured provider definition
   * @return the instantiated storage provider
   * @throws IllegalArgumentException if the definition's type is unknown or unsupported
   */
  StorageProvider create(ProviderDefinition definition);
}