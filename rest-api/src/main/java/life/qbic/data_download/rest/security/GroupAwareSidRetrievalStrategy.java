package life.qbic.data_download.rest.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import life.qbic.data_download.rest.security.acl.GroupSidProvider;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.SidRetrievalStrategyImpl;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.acls.model.SidRetrievalStrategy;
import org.springframework.security.core.Authentication;

/**
 * A {@link SidRetrievalStrategy} that, in addition to the default SIDs (principal + one
 * {@link GrantedAuthoritySid} per authority), derives one {@link GrantedAuthoritySid} per ACTIVE
 * user group SID of the authenticated user.
 * <p>
 * Group SIDs are NOT injected into the {@link Authentication} — they are derived per permission
 * check so that group grants/revocations take effect at the next check (subject to the ACL cache
 * TTL).
 */
public class GroupAwareSidRetrievalStrategy implements SidRetrievalStrategy {

  private final SidRetrievalStrategy defaultStrategy = new SidRetrievalStrategyImpl();
  private final GroupSidProvider groupSidProvider;

  public GroupAwareSidRetrievalStrategy(GroupSidProvider groupSidProvider) {
    this.groupSidProvider = Objects.requireNonNull(groupSidProvider,
        "groupSidProvider must not be null");
  }

  @Override
  public List<Sid> getSids(Authentication authentication) {
    List<Sid> sids = new ArrayList<>(defaultStrategy.getSids(authentication));
    String userId = authentication == null ? null : authentication.getName();
    if (userId != null && !userId.isBlank()) {
      groupSidProvider.listGroupSidsForUser(userId).stream()
          .filter(sid -> sid != null && !sid.isBlank())
          .map(GrantedAuthoritySid::new)
          .forEach(sids::add);
    }
    return sids;
  }
}