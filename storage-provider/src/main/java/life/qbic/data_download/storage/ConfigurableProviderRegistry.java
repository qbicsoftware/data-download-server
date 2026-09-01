package life.qbic.data_download.storage;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import life.qbic.data_download.storage.exception.ProviderException;

/**
 * A {@link ProviderRegistry} built from configured {@link ProviderDefinition}s.
 *
 * <p>For every enabled definition it asks the given {@link ProviderFactory} to instantiate the
 * {@link StorageProvider}, and keeps them indexed by provider id. {@link #getProvider(String)}
 * resolves the dataset to a provider id via a {@link DatasetProviderResolver}, then returns the
 * matching provider.
 */
public class ConfigurableProviderRegistry implements ProviderRegistry {

  private final Map<String, StorageProvider> providersById;
  private final DatasetProviderResolver resolver;

  public ConfigurableProviderRegistry(Collection<ProviderDefinition> definitions,
      ProviderFactory factory, DatasetProviderResolver resolver) {
    requireNonNull(factory, "factory must not be null");
    this.resolver = requireNonNull(resolver, "resolver must not be null");
    this.providersById = buildProviders(requireNonNull(definitions, "definitions must not be null"),
        factory);
  }

  @Override
  public StorageProvider getProvider(String datasetId) {
    requireNonNull(datasetId, "datasetId must not be null");
    String providerId = resolver.providerIdFor(datasetId)
        .orElseThrow(() -> new ProviderException("no provider configured for dataset " + datasetId));
    StorageProvider provider = providersById.get(providerId);
    if (provider == null) {
      throw new ProviderException("no provider with id " + providerId + " is configured");
    }
    return provider;
  }

  /**
   * The provider configured for the given provider id, if any.
   *
   * @param providerId the provider id
   * @return the provider, or empty if no such enabled provider is configured
   */
  public java.util.Optional<StorageProvider> provider(String providerId) {
    return java.util.Optional.ofNullable(providersById.get(providerId));
  }

  private static Map<String, StorageProvider> buildProviders(Collection<ProviderDefinition> definitions,
      ProviderFactory factory) {
    Map<String, StorageProvider> providers = new LinkedHashMap<>();
    for (ProviderDefinition definition : definitions) {
      if (!definition.enabled()) {
        continue;
      }
      if (providers.containsKey(definition.id())) {
        throw new IllegalArgumentException("duplicate provider id: " + definition.id());
      }
      providers.put(definition.id(), factory.create(definition));
    }
    return providers;
  }
}