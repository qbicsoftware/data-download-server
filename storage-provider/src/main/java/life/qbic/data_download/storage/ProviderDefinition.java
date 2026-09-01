package life.qbic.data_download.storage;

import static java.util.Objects.requireNonNull;

import java.util.Map;

/**
 * The configured definition of a storage provider, as bound from application configuration.
 *
 * <p>Each provider is identified by a unique {@code id}. The {@code type} selects the provider
 * implementation and, in turn, which entries of {@code properties} are required. A disabled
 * provider is not instantiated.
 *
 * @param id         the unique provider id (the key under {@code providers:})
 * @param type       the provider implementation type (e.g. {@code openbis}, {@code nfs}, {@code s3})
 * @param enabled    whether the provider is active
 * @param properties the type-specific configuration
 */
public record ProviderDefinition(String id, String type, boolean enabled,
                                 Map<String, Object> properties) {

  public ProviderDefinition {
    requireNonNull(id, "id must not be null");
    requireNonNull(type, "type must not be null");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (type.isBlank()) {
      throw new IllegalArgumentException("type must not be blank");
    }
    properties = properties == null ? Map.of() : Map.copyOf(properties);
  }
}