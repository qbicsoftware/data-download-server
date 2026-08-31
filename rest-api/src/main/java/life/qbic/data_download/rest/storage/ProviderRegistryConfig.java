package life.qbic.data_download.rest.storage;

import java.util.List;
import java.util.Optional;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.openbis.OpenBisStorageProvider;
import life.qbic.data_download.storage.ConfigurableProviderRegistry;
import life.qbic.data_download.storage.DatasetProviderResolver;
import life.qbic.data_download.storage.ProviderDefinition;
import life.qbic.data_download.storage.ProviderFactory;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the configured storage providers into a {@link ProviderRegistry}.
 *
 * <p>Currently only the {@code openbis} type is supported; it adapts the legacy
 * {@link MeasurementDataProvider}. The registry is consumed by the download endpoints.
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderRegistryConfig {

  @Bean
  public ProviderFactory storageProviderFactory(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider) {
    return definition -> switch (definition.type()) {
      case "openbis" -> new OpenBisStorageProvider(measurementDataProvider);
      default -> throw new IllegalArgumentException(
          "unknown storage provider type: " + definition.type());
    };
  }

  @Bean
  public DatasetProviderResolver datasetProviderResolver(ProviderProperties properties) {
    return new ConfigBackedDatasetProviderResolver(properties);
  }

  @Bean
  public ProviderRegistry providerRegistry(ProviderProperties properties,
      ProviderFactory storageProviderFactory,
      DatasetProviderResolver datasetProviderResolver) {
    List<ProviderDefinition> definitions = properties.getProviders().entrySet().stream()
        .map(e -> toDefinition(e.getKey(), e.getValue()))
        .toList();
    return new ConfigurableProviderRegistry(definitions, storageProviderFactory,
        datasetProviderResolver);
  }

  private static ProviderDefinition toDefinition(String id,
      ProviderProperties.Provider provider) {
    return new ProviderDefinition(id, provider.getType(), provider.isEnabled(),
        provider.getProperties());
  }

  /**
   * A resolver that serves datasets from the configured default provider, falling back to the sole
   * configured provider when no default is set.
   */
  private static final class ConfigBackedDatasetProviderResolver implements DatasetProviderResolver {

    private final ProviderProperties properties;

    ConfigBackedDatasetProviderResolver(ProviderProperties properties) {
      this.properties = properties;
    }

    @Override
    public Optional<String> providerIdFor(String datasetId) {
      if (properties.getDefaultProvider() != null) {
        return Optional.of(properties.getDefaultProvider());
      }
      if (properties.getProviders().size() == 1) {
        return properties.getProviders().keySet().stream().findFirst();
      }
      return Optional.empty();
    }
  }
}