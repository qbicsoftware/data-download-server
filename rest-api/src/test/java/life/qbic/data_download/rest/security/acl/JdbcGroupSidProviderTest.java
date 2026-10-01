package life.qbic.data_download.rest.security.acl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcGroupSidProviderTest {

  /** A {@link JdbcTemplate} test double that records the user id argument and returns a fixed list. */
  private static final class RecordingJdbcTemplate extends JdbcTemplate {

    private final List<String> result;
    private final List<Object> queriedArguments = new ArrayList<>();

    RecordingJdbcTemplate(List<String> result) {
      super(new DriverManagerDataSource());
      this.result = result;
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
      queriedArguments.add(args.length == 0 ? null : args[0]);
      @SuppressWarnings("unchecked")
      List<T> typedResult = (List<T>) result;
      return typedResult;
    }
  }

  @Test
  @DisplayName("the user id is passed to the query and returned group ids are prefixed with GROUP_")
  void userIdIsPassedAndGroupIdsArePrefixed() {
    RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("42", "1337"));
    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    List<String> sids = provider.listGroupSidsForUser("user-1");

    assertEquals(List.of("user-1"), jdbcTemplate.queriedArguments);
    assertEquals(List.of("GROUP_42", "GROUP_1337"), sids);
  }

  @Test
  @DisplayName("a null user id yields an empty list and the repository is not queried")
  void nullUserIdYieldsEmptyListWithoutQuerying() {
    RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("42"));
    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    assertTrue(provider.listGroupSidsForUser(null).isEmpty());
    assertTrue(jdbcTemplate.queriedArguments.isEmpty(), "the query must not run for a null user id");
  }

  @Test
  @DisplayName("a blank user id yields an empty list and the repository is not queried")
  void blankUserIdYieldsEmptyListWithoutQuerying() {
    RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate(List.of("42"));
    JdbcGroupSidProvider provider = new JdbcGroupSidProvider(jdbcTemplate);

    assertTrue(provider.listGroupSidsForUser("   ").isEmpty());
    assertTrue(jdbcTemplate.queriedArguments.isEmpty(), "the query must not run for a blank user id");
  }
}
