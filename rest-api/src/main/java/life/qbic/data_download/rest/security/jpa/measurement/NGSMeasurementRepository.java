package life.qbic.data_download.rest.security.jpa.measurement;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface NGSMeasurementRepository extends
    Repository<NGSMeasurementProject, String> {

  boolean existsByMeasurementCode(String code);
  Optional<NGSMeasurementProject> findByMeasurementCode(String code);



}
