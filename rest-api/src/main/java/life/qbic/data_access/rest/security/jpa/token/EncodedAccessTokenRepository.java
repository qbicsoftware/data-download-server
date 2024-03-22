package life.qbic.data_access.rest.security.jpa.token;

import org.springframework.data.jpa.repository.JpaRepository;


public interface EncodedAccessTokenRepository extends JpaRepository<EncodedAccessToken, Integer> {

}
