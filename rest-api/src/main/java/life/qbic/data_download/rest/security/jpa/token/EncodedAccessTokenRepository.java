package life.qbic.data_download.rest.security.jpa.token;

import java.util.Optional;
import org.springframework.data.repository.Repository;


public interface EncodedAccessTokenRepository extends Repository<EncodedAccessToken, Integer> {

  Optional<EncodedAccessToken> findByAccessTokenEquals(String accessToken);
}
