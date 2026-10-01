package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import life.qbic.data_download.rest.security.acl.GroupSidProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class GroupAwareSidRetrievalStrategyTest {

  @Test
  @DisplayName("default SIDs (principal and authorities) are preserved")
  void defaultSidsArePreserved() {
    GroupSidProvider provider = mock(GroupSidProvider.class);
    when(provider.listGroupSidsForUser("user-1")).thenReturn(List.of());

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
    GroupSidProvider provider = mock(GroupSidProvider.class);
    when(provider.listGroupSidsForUser("user-1"))
        .thenReturn(List.of("GROUP_42", "GROUP_1337"));

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
    GroupSidProvider provider = mock(GroupSidProvider.class);
    when(provider.listGroupSidsForUser("user-1")).thenReturn(List.of("GROUP_42"));
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(null);
    when(authentication.getPrincipal()).thenReturn("user-1");
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication)
        .getAuthorities();

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(2, sids.size());
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_ADMIN")), "authority SID expected");
    verify(provider, never()).listGroupSidsForUser(null);
  }

  @Test
  @DisplayName("a blank user id yields only the default SIDs and does not query the provider")
  void blankUserIdYieldsOnlyDefaultSids() {
    GroupSidProvider provider = mock(GroupSidProvider.class);
    when(provider.listGroupSidsForUser("user-1")).thenReturn(List.of("GROUP_42"));
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn("   ");
    when(authentication.getPrincipal()).thenReturn("user-1");
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))).when(authentication)
        .getAuthorities();

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(2, sids.size());
    assertTrue(sids.contains(new GrantedAuthoritySid("ROLE_ADMIN")), "authority SID expected");
    verify(provider, never()).listGroupSidsForUser("   ");
  }

  @Test
  @DisplayName("null and blank group SIDs from the provider are ignored")
  void nullAndBlankGroupSidsAreIgnored() {
    GroupSidProvider provider = mock(GroupSidProvider.class);
    when(provider.listGroupSidsForUser("user-1"))
        .thenReturn(Arrays.asList("GROUP_42", null, "   ", "GROUP_7"));

    var authentication = new TestingAuthenticationToken("user-1", "credentials", "ROLE_ADMIN");
    authentication.setAuthenticated(true);

    List<Sid> sids = new GroupAwareSidRetrievalStrategy(provider).getSids(authentication);

    assertEquals(4, sids.size(), "principal + one authority + two valid group SIDs expected");
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_42")));
    assertTrue(sids.contains(new GrantedAuthoritySid("GROUP_7")));
  }
}