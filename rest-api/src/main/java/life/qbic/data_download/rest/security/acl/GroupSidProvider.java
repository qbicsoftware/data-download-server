package life.qbic.data_download.rest.security.acl;

import java.util.List;

/**
 * Provides the Spring Security ACL SIDs for all ACTIVE user groups a user is a member of.
 * <p>
 * Group access is persisted in the ACL as a {@code GrantedAuthoritySid} with the reserved string
 * {@code GROUP_<groupId>}. This port exposes exactly those SIDs so the ACL SID derivation can
 * match group ACEs on project ACLs.
 */
public interface GroupSidProvider {

  /**
   * The reserved SID prefix for user group authorities. This is a contract with the data-manager
   * (see {@code GroupSidProvider.GROUP_SID_PREFIX}); a mismatch silently denies access.
   */
  String GROUP_SID_PREFIX = "GROUP_";

  /**
   * Returns {@code GROUP_<groupId>} for every ACTIVE group membership of the given user.
   *
   * @param userId the stable user id (never the e-mail address)
   * @return the group SIDs of the user's ACTIVE group memberships, or an empty list if the user id
   *     is null or blank
   */
  List<String> listGroupSidsForUser(String userId);
}