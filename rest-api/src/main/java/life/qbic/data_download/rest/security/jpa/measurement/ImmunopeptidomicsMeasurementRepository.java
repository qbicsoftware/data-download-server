package life.qbic.data_download.rest.security.jpa.measurement;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ImmunopeptidomicsMeasurementRepository extends
    Repository<ImmunopeptidomicsMeasurementProject, String> {

  boolean existsByMeasurementCode(String code);
  Optional<ImmunopeptidomicsMeasurementProject> findByMeasurementCode(String code);


}
