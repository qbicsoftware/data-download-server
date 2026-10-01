package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import life.qbic.data_download.rest.security.acl.GroupSidProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class GroupAwareSidRetrievalStrategyTest {

  /**
   * A {@link GroupSidProvider} test double that returns a fixed list and records every user id it
   * was asked about, so tests can assert the provider is (not) queried.
   */
  private static final class RecordingGroupSidProvider implements GroupSidProvider {

    private final List<String> groupSids;
    private final List<String> queriedUserIds = new ArrayList<>();

    RecordingGroupSidProvider(List<String> groupSids) {
      this.groupSids = groupSids;
    }

    @Override
    public List<String> listGroupSidsForUser(String userId) {
      queriedUserIds.add(userId);
      return groupSids;
    }
  }

  /**
   * Authentication with an explicitly controllable {@link #getName()} so the null/blank user-id
   * branches can be exercised while the principal and authorities stay intact.
   */
  private static final class NamedAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final String name;

    NamedAuthenticationToken(String name, Object principal, String... authorities) {
      super(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
      this.name = name;
      this.principal = principal;
      setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
      return "credentials";
    }

    @Override
    public Object getPrincipal() {
      return principal;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  @Test
  @DisplayName("default SIDs (principal and authorities) are preserved")
  void defaultSidsArePreserved() {
    var provider = new RecordingGroupSidProvider(List.of());

    var authentication = new TestingAuthenticationToken("user-1", "credentials",
        "ROLE_ADMIN", "ROLE_PROJECT_MANAGER");
    authentication.setAuthenticated(true);

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(3, sids.size());
    assertEquals(new PrincipalSid("user-1"), sids.get(0));
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_ADMIN")));
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_PROJECT_MANAGER")));
  }

  @Test
  @DisplayName("one GrantedAuthoritySid is appended per group SID returned by the provider")
  void groupSidsAreAppended() {
    var provider = new RecordingGroupSidProvider(List.of("GROUP_42", "GROUP_1337"));

    var authentication = new TestingAuthenticationToken("user-1", "credentials", "ROLE_ADMIN");
    authentication.setAuthenticated(true);

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(4, sids.size());
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_42")));
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_1337")));
  }

  @Test
  @DisplayName("a null user id yields only the default SIDs and does not query the provider")
  void nullUserIdYieldsOnlyDefaultSids() {
    var provider = new RecordingGroupSidProvider(List.of("GROUP_42"));

    Authentication authentication = new NamedAuthenticationToken(null, "user-1", "ROLE_ADMIN");

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(2, sids.size());
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_ADMIN")));
    assertTrue(provider.queriedUserIds.isEmpty(), "provider must not be queried for a null user id");
  }

  @Test
  @DisplayName("a blank user id yields only the default SIDs and does not query the provider")
  void blankUserIdYieldsOnlyDefaultSids() {
    var provider = new RecordingGroupSidProvider(List.of("GROUP_42"));

    Authentication authentication = new NamedAuthenticationToken("   ", "user-1", "ROLE_ADMIN");

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(2, sids.size());
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_ADMIN")));
    assertTrue(provider.queriedUserIds.isEmpty(),
        "provider must not be queried for a blank user id");
  }

  @Test
  @DisplayName("null and blank group SIDs from the provider are ignored")
  void nullAndBlankGroupSidsAreIgnored() {
    var provider = new RecordingGroupSidProvider(
        Arrays.asList("GROUP_42", null, "   ", "GROUP_7"));

    var authentication = new TestingAuthenticationToken("user-1", "credentials", "ROLE_ADMIN");
    authentication.setAuthenticated(true);

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(4, sids.size(), "principal + one authority + two valid group SIDs expected");
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_42")));
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_7")));
  }
}
