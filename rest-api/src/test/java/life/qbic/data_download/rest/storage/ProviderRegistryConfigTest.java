package life.qbic.data_download.rest.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.List;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.openbis.OpenBisStorageProvider;
import life.qbic.data_download.storage.ProviderRegistry;
import life.qbic.data_download.storage.StorageProvider;
import life.qbic.data_download.storage.exception.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ProviderRegistryConfigTest {

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(TestMeasurementProviderConfig.class, ProviderRegistryConfig.class);

  @Test
  void registryResolvesDatasetToConfiguredOpenbisProvider() {
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

  @Test
  void registryWithoutProvidersIsStartableButResolvesNothing() {
    context.run(ctx -> {
      assertThat(ctx).hasSingleBean(ProviderRegistry.class);
      ProviderRegistry registry = ctx.getBean(ProviderRegistry.class);
      assertThatThrownBy(() -> registry.getProvider("M-1"))
          .isInstanceOf(ProviderException.class);
    });
  }

  /**
   * A fake {@link MeasurementDataProvider} so the openbis provider factory can be constructed
   * without a real openBIS connection.
   */
  @Configuration
  static class TestMeasurementProviderConfig {

    @Bean("measurementDataProvider")
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
  }
}