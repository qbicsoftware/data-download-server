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
      @Qualifier("openbisSessionFactory") SessionFactory sessionFactory,
      ProviderProperties providerProperties) {
    return definition -> {
      ProviderProperties.Provider provider = providerProperties.getProviders().get(definition.id());
      if (provider == null) {
        throw new IllegalArgumentException("No configuration found for provider: " + definition.id());
      }
      
      return switch (definition.type()) {
        case "openbis" -> new OpenBisStorageProvider(measurementDataProvider);
        case "openbis-nfs" -> createOpenBisNfsProvider(provider, sessionFactory);
        default -> throw new IllegalArgumentException(
            "unknown storage provider type: " + definition.type());
      };
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
        .map(e -> new ProviderDefinition(e.getKey(), e.getValue().getType(), 
            e.getValue().isEnabled(), e.getValue().getAdditionalProperties()))
        .toList();
    return new ConfigurableProviderRegistry(definitions, storageProviderFactory,
        datasetProviderResolver);
  }

  /**
   * Creates an OpenBisNfsStorageProvider from the given provider configuration. Requires 
   * provider-specific openBIS configuration (user, server, filename) and a {@code mount-path}
   * specifying the root directory where openBIS data is mounted.
   */
  private static OpenBisNfsStorageProvider createOpenBisNfsProvider(
      ProviderProperties.Provider provider, SessionFactory sessionFactory) {
    // Validate and extract user configuration
    if (provider.getUser() == null) {
      throw new IllegalArgumentException("openbis-nfs provider requires 'user' configuration");
    }
    String userName = provider.getUser().getName();
    String password = provider.getUser().getPassword();
    if (userName == null || password == null) {
      throw new IllegalArgumentException(
          "openbis-nfs provider requires 'user.name' and 'user.password'");
    }
    
    // Validate and extract server configuration
    if (provider.getServer() == null) {
      throw new IllegalArgumentException("openbis-nfs provider requires 'server' configuration");
    }
    String applicationUrl = provider.getServer().getApplicationUrl();
    String dataStoreUrls = provider.getServer().getDatastoreUrls();
    if (applicationUrl == null || dataStoreUrls == null) {
      throw new IllegalArgumentException(
          "openbis-nfs provider requires 'server.application-url' and 'server.datastore-urls'");
    }
    
    // Extract filename configuration (optional)
    String ignoredPrefix = "original";
    if (provider.getFilename() != null && provider.getFilename().getIgnoredPrefix() != null) {
      ignoredPrefix = provider.getFilename().getIgnoredPrefix();
    }
    
    // Validate and extract mount-path
    String mountPathStr = provider.getMountPath();
    if (mountPathStr == null) {
      throw new IllegalArgumentException("openbis-nfs provider requires 'mount-path'");
    }
    Path mountPath = Path.of(mountPathStr);
    
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
