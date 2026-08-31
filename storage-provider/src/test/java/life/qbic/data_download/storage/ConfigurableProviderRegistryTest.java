package life.qbic.data_download.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import life.qbic.data_download.storage.exception.ProviderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigurableProviderRegistryTest {

  private final FakeProviderFactory factory = new FakeProviderFactory();

  @Test
  @DisplayName("registry resolves a dataset to its provider via the resolver")
  void resolvesDatasetToProvider() {
    ConfigurableProviderRegistry registry = newRegistry(
        definitions(openbis("openbis-1")),
        dataset -> Optional.of("openbis-1"));

    assertSame(factory.provider("openbis-1"), registry.getProvider("M-1"));
  }

  @Test
  @DisplayName("registry skips disabled providers")
  void skipsDisabledProviders() {
    ConfigurableProviderRegistry registry = newRegistry(
        List.of(definition("openbis-1", true), definition("openbis-2", false)),
        dataset -> Optional.of("openbis-2"));

    assertTrue(registry.provider("openbis-2").isEmpty());
    assertThrows(ProviderException.class, () -> registry.getProvider("M-1"));
  }

  @Test
  @DisplayName("registry throws ProviderException when no provider is mapped for a dataset")
  void throwsWhenNoProviderMapped() {
    ConfigurableProviderRegistry registry = newRegistry(
        definitions(openbis("openbis-1")),
        dataset -> Optional.empty());

    assertThrows(ProviderException.class, () -> registry.getProvider("M-1"));
  }

  @Test
  @DisplayName("registry throws ProviderException when the resolved id is not configured")
  void throwsWhenResolvedIdNotConfigured() {
    ConfigurableProviderRegistry registry = newRegistry(
        definitions(openbis("openbis-1")),
        dataset -> Optional.of("missing"));

    assertThrows(ProviderException.class, () -> registry.getProvider("M-1"));
  }

  @Test
  @DisplayName("registry rejects duplicate provider ids")
  void rejectsDuplicateProviderIds() {
    ProviderFactory factory = new FakeProviderFactory();
    assertThrows(IllegalArgumentException.class, () -> new ConfigurableProviderRegistry(
        List.of(openbis("openbis-1"), openbis("openbis-1")),
        factory, dataset -> Optional.of("openbis-1")));
  }

  @Test
  @DisplayName("factory is asked once per enabled provider with the full definition")
  void factoryReceivesDefinition() {
    new ConfigurableProviderRegistry(
        definitions(openbis("openbis-1")),
        factory, dataset -> Optional.of("openbis-1"));

    assertEquals(1, factory.calls);
    assertEquals("openbis-1", factory.lastDefinition.id());
    assertEquals("openbis", factory.lastDefinition.type());
  }

  private ConfigurableProviderRegistry newRegistry(List<ProviderDefinition> definitions,
      DatasetProviderResolver resolver) {
    return new ConfigurableProviderRegistry(definitions, factory, resolver);
  }

  private static List<ProviderDefinition> definitions(ProviderDefinition... defs) {
    return List.of(defs);
  }

  private static ProviderDefinition openbis(String id) {
    return definition(id, true);
  }

  private static ProviderDefinition definition(String id, boolean enabled) {
    return new ProviderDefinition(id, "openbis", enabled,
        Map.of("session-timeout", 3600));
  }

  /** A fake factory that records calls and returns a distinct provider per id. */
  private static final class FakeProviderFactory implements ProviderFactory {

    private final Map<String, StorageProvider> providers = new java.util.HashMap<>();
    private int calls;
    private ProviderDefinition lastDefinition;

    FakeProviderFactory() {
    }

    StorageProvider provider(String id) {
      return providers.get(id);
    }

    @Override
    public StorageProvider create(ProviderDefinition definition) {
      calls++;
      lastDefinition = definition;
      return providers.computeIfAbsent(definition.id(), i -> new FakeStorageProvider(i));
    }
  }

  private static final class FakeStorageProvider implements StorageProvider {

    private final String id;

    FakeStorageProvider(String id) {
      this.id = id;
    }

    @Override
    public List<FileInfo> listFiles(String datasetId) {
      return List.of();
    }

    @Override
    public DataFile getFile(String datasetId, int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileInfo getFileMetadata(String datasetId, int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "FakeStorageProvider[" + id + "]";
    }
  }
}