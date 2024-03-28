package life.qbic.data_download.rest.security;

/**
 * Encodes tokens and checks for matches
 */
public interface TokenMatcher {

  /**
   * Checks whether the provided token matches the encoded token
   *
   * @param token the raw token to check
   * @param encodedToken the encoded token to check against
   * @return true if the token matches; false otherwise
   */
  boolean matches(char[] token, String encodedToken);
}
