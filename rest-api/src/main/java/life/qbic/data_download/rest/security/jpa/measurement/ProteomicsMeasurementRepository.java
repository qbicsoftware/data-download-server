package life.qbic.data_download.rest.security.jpa.measurement;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ProteomicsMeasurementRepository extends
    Repository<ProteomicsMeasurementProject, String> {

  boolean existsByMeasurementCode(String code);
  Optional<ProteomicsMeasurementProject> findByMeasurementCode(String code);



}
