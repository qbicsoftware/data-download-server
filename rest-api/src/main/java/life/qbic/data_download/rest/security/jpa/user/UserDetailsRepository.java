package life.qbic.data_download.rest.security.jpa.user;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.Repository;

public interface UserDetailsRepository extends Repository<QBiCUserDetails, Integer> {
  Optional<QBiCUserDetails> findById(String id);
}
