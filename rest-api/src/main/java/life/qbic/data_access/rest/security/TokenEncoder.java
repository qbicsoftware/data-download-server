package life.qbic.data_access.rest.security;

/**
 * Encodes tokens and checks for matches
 */
public interface TokenEncoder {

  /**
   * Encodes a token
   *
   * @param token the token to encode
   * @return an encoded representation of the token
   */
  String encode(char[] token);

  /**
   * Checks whether the provided token matches the encoded token
   *
   * @param token the raw token to check
   * @param encodedToken the encoded token to check against
   * @return true if the token matches; false otherwise
   */
  boolean matches(char[] token, String encodedToken);
}
