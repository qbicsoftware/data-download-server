package life.qbic.data_download.rest.security.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcGroupSidProviderTest {

  private static final String QUERY = """
      SELECT m.group_id
      FROM group_membership m
      JOIN user_group g ON g.id = m.group_id
      WHERE m.user_id = ? AND g.status = 'ACTIVE'
      """;

  @Test
  @DisplayName("the user id is passed to the query and returned group ids are prefixed with GROUP_")
  void userIdIsPassedAndGroupIdsArePrefixed() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    when(jdbcTemplate.queryForList(QUERY, String.class, "user-1"))
        .thenReturn(List.of("42", "1337"));

    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    List<String> sids = provider.listGroupSidsForUser("user-1");

    verify(jdbcTemplate).queryForList(QUERY, String.class, "user-1");
    assertEquals(List.of("GROUP_42", "GROUP_1337"), sids);
  }

  @Test
  @DisplayName("a null user id yields an empty list and the repository is not queried")
  void nullUserIdYieldsEmptyListWithoutQuerying() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    assertTrue(provider.listGroupSidsForUser(null).isEmpty());
    verify(jdbcTemplate, never()).queryForList(eq(QUERY), eq(String.class), any());
  }

  @Test
  @DisplayName("a blank user id yields an empty list and the repository is not queried")
  void blankUserIdYieldsEmptyListWithoutQuerying() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    assertTrue(provider.listGroupSidsForUser("   ").isEmpty());
    verify(jdbcTemplate, never()).queryForList(eq(QUERY), eq(String.class), any());
  }
}