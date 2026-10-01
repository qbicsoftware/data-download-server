package life.qbic.data_download.rest.security.acl;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A {@link GroupSidProvider} that looks up a user's ACTIVE group memberships in the
 * access-management database.
 * <p>
 * The group tables and the ACL tables share the access-management datasource
 * ({@code securityDataSource}), so this lookup cannot diverge from the ACL data it enriches.
 */
public class JdbcGroupSidProvider implements GroupSidProvider {

  private static final String ACTIVE_GROUP_MEMBERSHIPS_QUERY = """
      SELECT m.group_id
      FROM group_membership m
      JOIN user_group g ON g.id = m.group_id
      WHERE m.user_id = ? AND g.status = 'ACTIVE'
      """;

  private final JdbcTemplate jdbcTemplate;

  public JdbcGroupSidProvider(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  public List<String> listGroupSidsForUser(String userId) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    return jdbcTemplate.queryForList(ACTIVE_GROUP_MEMBERSHIPS_QUERY, String.class, userId)
        .stream()
        .map(groupId -> GROUP_SID_PREFIX + groupId)
        .toList();
  }
}