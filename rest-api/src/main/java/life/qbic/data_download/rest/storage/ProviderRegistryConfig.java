package life.qbic.data_download.rest.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.openbis.OpenBisNfsStorageProvider;
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
 * <p>Supported provider types:
 * <ul>
 *   <li>{@code openbis} - uses openBIS DSS HTTP API for metadata and file streaming</li>
 *   <li>{@code openbis-nfs} - uses openBIS for metadata, streams files from mounted NFS via NIO</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderRegistryConfig {

  @Bean
  public ProviderFactory storageProviderFactory(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider) {
    return definition -> switch (definition.type()) {
      case "openbis" -> new OpenBisStorageProvider(measurementDataProvider);
      case "openbis-nfs" -> {
        yield createOpenBisNfsProvider(definition, measurementDataProvider);
      }
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
   * Creates an OpenBisNfsStorageProvider from the given definition. Requires a {@code mount-path}
   * property specifying the root directory where openBIS data is mounted.
   */
  private static OpenBisNfsStorageProvider createOpenBisNfsProvider(
      ProviderDefinition definition, MeasurementDataProvider measurementDataProvider) {
    Object mountPathObj = definition.properties().get("mount-path");
    if (mountPathObj == null) {
      throw new IllegalArgumentException(
          "openbis-nfs provider requires 'mount-path' property: " + definition.id());
    }
    Path mountPath = Path.of(mountPathObj.toString());
    return new OpenBisNfsStorageProvider(measurementDataProvider, mountPath);
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