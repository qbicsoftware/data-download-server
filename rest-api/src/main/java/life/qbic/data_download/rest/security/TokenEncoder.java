package life.qbic.data_download.rest.security;

/**
 * Encodes QBiC Access Tokens
 */
public interface TokenEncoder {

  String encode(String token);
}
