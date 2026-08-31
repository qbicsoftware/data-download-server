package life.qbic.data_download.rest.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.openbis.OpenBisStorageProvider;
import life.qbic.data_download.openbis.SessionFactory;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for ProviderRegistryConfig. Note: openbis-nfs provider type requires a real OpenBisConnector
 * bean which is complex to mock in unit tests. Integration tests verify the openbis-nfs provider
 * works end-to-end with a real openBIS connection.
 */
class ProviderRegistryConfigOpenBisNfsTest {

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class, ProviderRegistryConfig.class);

  @Test
  @DisplayName("openbis provider type works")
  void openBisProviderTypeWorks() {
    context
        .withPropertyValues(
            "providers.default-provider=openbis-1",
            "providers.providers.openbis-1.type=openbis",
            "providers.providers.openbis-1.enabled=true")
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(ProviderRegistry.class);
          ProviderRegistry registry = ctx.getBean(ProviderRegistry.class);
          StorageProvider provider = registry.getProvider("M-1");
          assertThat(provider).isInstanceOf(OpenBisStorageProvider.class);
        });
  }

  /**
   * Test configuration with fake beans for testing.
   */
  @Configuration
  static class TestConfig {

    @Bean
    MeasurementDataProvider measurementDataProvider() {
      return new MeasurementDataProvider() {
        @Override
        public MeasurementData loadData(MeasurementId measurementId) {
          return () -> new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public List<FileInfo> listFiles(MeasurementId measurementId) {
          return List.of();
        }

        @Override
        public life.qbic.data_download.measurements.api.DataFile loadFile(
            MeasurementId measurementId, FileInfo fileInfo) {
          return null;
        }
      };
    }

    @Bean("openbisSessionFactory")
    SessionFactory openbisSessionFactory() {
      // Return a stub SessionFactory for tests that don't actually use it
      return new SessionFactory("http://localhost", "test", "test");
    }
  }
}
