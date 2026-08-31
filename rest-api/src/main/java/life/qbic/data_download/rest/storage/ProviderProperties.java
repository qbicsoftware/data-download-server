package life.qbic.data_download.rest.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code providers.*} application properties.
 *
 * <p>Each key under {@code providers.providers} is a provider id, and its {@code type} selects the
 * provider implementation. {@code providers.default-provider} optionally names the provider id used
 * to serve datasets that are not explicitly mapped.
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

  private final Map<String, Provider> providers = new LinkedHashMap<>();
  private String defaultProvider;

  public Map<String, Provider> getProviders() {
    return providers;
  }

  public String getDefaultProvider() {
    return defaultProvider;
  }

  public void setDefaultProvider(String defaultProvider) {
    this.defaultProvider = defaultProvider;
  }

  /**
   * A single configured provider.
   */
  public static class Provider {

    private String type;
    private boolean enabled = true;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Map<String, Object> getProperties() {
      return properties;
    }
  }
}