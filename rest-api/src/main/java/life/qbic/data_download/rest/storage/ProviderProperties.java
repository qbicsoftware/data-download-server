package life.qbic.data_download.rest.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code providers.*} application properties.
 *
 * <p>Each key under {@code providers.instances} is a provider id, and its {@code type} selects the
 * provider implementation. {@code providers.default-provider} optionally names the provider id used
 * to serve datasets that are not explicitly mapped.
 */
@ConfigurationProperties(prefix = "providers")
public class ProviderProperties {

  private final Map<String, Provider> instances = new LinkedHashMap<>();
  private String defaultProvider;

  public Map<String, Provider> getInstances() {
    return instances;
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
    private UserConfig user;
    private ServerConfig server;
    private FilenameConfig filename;
    private Integer sessionTimeout;
    private String mountPath;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

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

    public UserConfig getUser() {
      return user;
    }

    public void setUser(UserConfig user) {
      this.user = user;
    }

    public ServerConfig getServer() {
      return server;
    }

    public void setServer(ServerConfig server) {
      this.server = server;
    }

    public FilenameConfig getFilename() {
      return filename;
    }

    public void setFilename(FilenameConfig filename) {
      this.filename = filename;
    }

    public Integer getSessionTimeout() {
      return sessionTimeout;
    }

    public void setSessionTimeout(Integer sessionTimeout) {
      this.sessionTimeout = sessionTimeout;
    }

    public String getMountPath() {
      return mountPath;
    }

    public void setMountPath(String mountPath) {
      this.mountPath = mountPath;
    }

    public Map<String, Object> getAdditionalProperties() {
      return additionalProperties;
    }
  }

  /**
   * User credentials configuration.
   */
  public static class UserConfig {
    private String name;
    private String password;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }
  }

  /**
   * Server configuration.
   */
  public static class ServerConfig {
    private String applicationUrl;
    private String datastoreUrls;

    public String getApplicationUrl() {
      return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
      this.applicationUrl = applicationUrl;
    }

    public String getDatastoreUrls() {
      return datastoreUrls;
    }

    public void setDatastoreUrls(String datastoreUrls) {
      this.datastoreUrls = datastoreUrls;
    }
  }

  /**
   * Filename configuration.
   */
  public static class FilenameConfig {
    private String ignoredPrefix;

    public String getIgnoredPrefix() {
      return ignoredPrefix;
    }

    public void setIgnoredPrefix(String ignoredPrefix) {
      this.ignoredPrefix = ignoredPrefix;
    }
  }
}