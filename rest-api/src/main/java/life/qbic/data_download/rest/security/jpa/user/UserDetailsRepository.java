package life.qbic.data_download.rest.security.jpa.user;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface UserDetailsRepository extends CrudRepository<QBiCUserDetails, Integer> {
  Optional<QBiCUserDetails> findById(String id);
}
