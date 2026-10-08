package life.qbic.data_download.rest.security.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Optional;
import life.qbic.data_download.rest.security.jpa.measurement.ImmunopeptidomicsMeasurementProject;
import life.qbic.data_download.rest.security.jpa.measurement.ImmunopeptidomicsMeasurementRepository;
import life.qbic.data_download.rest.security.jpa.measurement.NGSMeasurementProject;
import life.qbic.data_download.rest.security.jpa.measurement.NGSMeasurementRepository;
import life.qbic.data_download.rest.security.jpa.measurement.ProteomicsMeasurementProject;
import life.qbic.data_download.rest.security.jpa.measurement.ProteomicsMeasurementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QBiCMeasurementMappingServiceTest {

  private static final String MEASUREMENT_CODE = "IPQ0001AO-123456789";
  private static final String PROJECT_ID = "c0ffee00-0000-0000-0000-000000000000";

  @Test
  @DisplayName("an immunopeptidomics measurement maps to its project id")
  void immunopeptidomicsMeasurementMapsToProject() {
    var service = new QBiCMeasurementMappingService(
        new EmptyNgsRepository(),
        new EmptyProteomicsRepository(),
        new FakeImmunopeptidomicsRepository(Optional.of(immunopeptidomics(PROJECT_ID))));

    assertEquals(Optional.of(PROJECT_ID), service.projectIdForMeasurement(MEASUREMENT_CODE));
  }

  @Test
  @DisplayName("a measurement unknown to every repository yields an empty result")
  void unknownMeasurementYieldsEmpty() {
    var service = new QBiCMeasurementMappingService(
        new EmptyNgsRepository(),
        new EmptyProteomicsRepository(),
        new FakeImmunopeptidomicsRepository(Optional.empty()));

    assertTrue(service.projectIdForMeasurement(MEASUREMENT_CODE).isEmpty());
  }

  @Test
  @DisplayName("a blank project id is not returned")
  void blankProjectIdIsFiltered() {
    var service = new QBiCMeasurementMappingService(
        new EmptyNgsRepository(),
        new EmptyProteomicsRepository(),
        new FakeImmunopeptidomicsRepository(Optional.of(immunopeptidomics("   "))));

    assertTrue(service.projectIdForMeasurement(MEASUREMENT_CODE).isEmpty());
  }

  private static ImmunopeptidomicsMeasurementProject immunopeptidomics(String projectId) {
    ImmunopeptidomicsMeasurementProject project = new ImmunopeptidomicsMeasurementProject();
    setField(project, "projectId", projectId);
    return project;
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** These repositories are only present so the service can be constructed. */
  private static final class EmptyNgsRepository implements NGSMeasurementRepository {

    @Override
    public boolean existsByMeasurementCode(String code) {
      return false;
    }

    @Override
    public Optional<NGSMeasurementProject> findByMeasurementCode(String code) {
      return Optional.empty();
    }
  }

  private static final class EmptyProteomicsRepository implements ProteomicsMeasurementRepository {

    @Override
    public boolean existsByMeasurementCode(String code) {
      return false;
    }

    @Override
    public Optional<ProteomicsMeasurementProject> findByMeasurementCode(String code) {
      return Optional.empty();
    }
  }

  private static final class FakeImmunopeptidomicsRepository implements
      ImmunopeptidomicsMeasurementRepository {

    private final Optional<ImmunopeptidomicsMeasurementProject> result;

    FakeImmunopeptidomicsRepository(Optional<ImmunopeptidomicsMeasurementProject> result) {
      this.result = result;
    }

    @Override
    public boolean existsByMeasurementCode(String code) {
      return result.isPresent();
    }

    @Override
    public Optional<ImmunopeptidomicsMeasurementProject> findByMeasurementCode(String code) {
      return result;
    }
  }
}
