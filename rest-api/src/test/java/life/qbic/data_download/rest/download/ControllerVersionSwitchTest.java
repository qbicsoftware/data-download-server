package life.qbic.data_download.rest.download;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import life.qbic.data_download.measurements.api.DataFile;
import life.qbic.data_download.measurements.api.FileInfo;
import life.qbic.data_download.measurements.api.MeasurementData;
import life.qbic.data_download.measurements.api.MeasurementDataProvider;
import life.qbic.data_download.measurements.api.MeasurementId;
import life.qbic.data_download.storage.ProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ControllerVersionSwitchTest {

  private static final String[] REQUIRED_PROPERTIES = {
      "server.memory.download.buffer=1048576",
      "server.download.queue.capacity=64",
  };

  private final ApplicationContextRunner context = new ApplicationContextRunner()
      .withUserConfiguration(TestConfig.class)
      .withPropertyValues(REQUIRED_PROPERTIES);

  @Test
  @DisplayName("V1 controller is active by default (matchIfMissing)")
  void v1ActiveByDefault() {
    context.run(ctx -> {
      assertThat(ctx).hasSingleBean(MeasurementFileController.class);
      assertThat(ctx).doesNotHaveBean(MeasurementFileControllerV2.class);
    });
  }

  @Test
  @DisplayName("V1 controller is active when explicitly set to v1")
  void v1ActiveWhenSet() {
    context
        .withPropertyValues("download.controller-version=v1")
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(MeasurementFileController.class);
          assertThat(ctx).doesNotHaveBean(MeasurementFileControllerV2.class);
        });
  }

  @Test
  @DisplayName("V2 controller is active when set to v2")
  void v2ActiveWhenSet() {
    context
        .withPropertyValues("download.controller-version=v2")
        .run(ctx -> {
          assertThat(ctx).hasSingleBean(MeasurementFileControllerV2.class);
          assertThat(ctx).doesNotHaveBean(MeasurementFileController.class);
        });
  }

  @Configuration
  @Import({MeasurementFileController.class, MeasurementFileControllerV2.class})
  static class TestConfig {

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
        public DataFile loadFile(MeasurementId measurementId, FileInfo fileInfo) {
          return null;
        }
      };
    }

    @Bean
    ProviderRegistry providerRegistry() {
      return datasetId -> new life.qbic.data_download.storage.StorageProvider() {
        @Override
        public List<life.qbic.data_download.storage.FileInfo> listFiles(String dsId) {
          return List.of();
        }

        @Override
        public life.qbic.data_download.storage.DataFile getFile(String dsId, int index) {
          return null;
        }

        @Override
        public life.qbic.data_download.storage.FileInfo getFileMetadata(String dsId, int index) {
          return null;
        }
      };
    }

    @Bean
    StorageFileIndex storageFileIndex(ProviderRegistry providerRegistry) {
      return new StorageFileIndex(providerRegistry, Duration.ofMinutes(1));
    }

    @Bean
    MeasurementFileIndex measurementFileIndex(MeasurementDataProvider measurementDataProvider) {
      return new MeasurementFileIndex(measurementDataProvider, Duration.ofMinutes(1));
    }

    @Bean
    life.qbic.data_download.rest.download.ByteRange byteRange() {
      return new life.qbic.data_download.rest.download.ByteRange();
    }
  }
}
