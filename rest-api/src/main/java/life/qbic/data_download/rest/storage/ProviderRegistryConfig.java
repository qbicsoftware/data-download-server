package life.qbic.data_download.rest.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.openbis.OpenBisConnector;
import life.qbic.data_download.openbis.OpenBisNfsStorageProvider;
import life.qbic.data_download.openbis.OpenBisStorageProvider;
import life.qbic.data_download.openbis.SessionFactory;
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
 *
 * <p>Each provider can have its own openBIS configuration (credentials, server URLs, etc.) as
 * specified in the architecture document.
 */
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderRegistryConfig {

  @Bean
  public ProviderFactory storageProviderFactory(
      @Qualifier("measurementDataProvider") MeasurementDataProvider measurementDataProvider,
      @Qualifier("openbisSessionFactory") SessionFactory sessionFactory) {
    return definition -> switch (definition.type()) {
      case "openbis" -> new OpenBisStorageProvider(measurementDataProvider);
      case "openbis-nfs" -> createOpenBisNfsProvider(definition, sessionFactory);
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
   * Creates an OpenBisNfsStorageProvider from the given definition. Requires provider-specific
   * openBIS configuration (user, server, filename, session-timeout) and a {@code mount-path}
   * property specifying the root directory where openBIS data is mounted.
   */
  private static OpenBisNfsStorageProvider createOpenBisNfsProvider(
      ProviderDefinition definition, SessionFactory sessionFactory) {
    // Extract openBIS configuration from provider properties
    String userName = getRequiredProperty(definition, "user.name");
    String password = getRequiredProperty(definition, "user.password");
    String applicationUrl = getRequiredProperty(definition, "server.application-url");
    String dataStoreUrls = getRequiredProperty(definition, "server.datastore-urls");
    String ignoredPrefix = getProperty(definition, "filename.ignored-prefix", "original");
    
    // Extract mount-path
    Object mountPathObj = definition.properties().get("mount-path");
    if (mountPathObj == null) {
      throw new IllegalArgumentException(
          "openbis-nfs provider requires 'mount-path' property: " + definition.id());
    }
    Path mountPath = Path.of(mountPathObj.toString());
    
    // Create provider-specific OpenBisConnector
    List<String> dataStoreUrlList = List.of(dataStoreUrls.split(","));
    OpenBisConnector connector = new OpenBisConnector(
        sessionFactory,
        applicationUrl,
        dataStoreUrlList,
        ignoredPrefix
    );
    
    return new OpenBisNfsStorageProvider(connector, mountPath);
  }

  private static String getRequiredProperty(ProviderDefinition definition, String key) {
    Object value = definition.properties().get(key);
    if (value == null) {
      throw new IllegalArgumentException(
          "Provider '" + definition.id() + "' requires property '" + key + "'");
    }
    return value.toString();
  }

  private static String getProperty(ProviderDefinition definition, String key, String defaultValue) {
    Object value = definition.properties().get(key);
    return value != null ? value.toString() : defaultValue;
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