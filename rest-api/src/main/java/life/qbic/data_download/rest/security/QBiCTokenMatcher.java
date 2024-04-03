package life.qbic.data_download.rest.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A token encoder
 */
public class QBiCTokenMatcher implements TokenMatcher {

  private static final int ITERATION_COUNT_INDEX = 0; // the index of the iteration count in the encoded token
  private static final int SALT_INDEX = 1; // the index of the salt content in the encoded token
  private static final int HASH_INDEX = 2; // the index of the hash content in the encoded token

  private record EncryptionSettings(int iterationCount, String cipher, int keyBitSize) {}

  private static final EncryptionSettings ENCRYPTION_SETTINGS = new EncryptionSettings(
      100_000,
      "AES",
      256
  );

  @Override
  public boolean matches(char[] token, String encodedToken) {
    byte[] readSalt = readSalt(encodedToken);
    int iterationCount = readIterationCount(encodedToken);
    byte[] asEncoded = pbe(token, readSalt, iterationCount);
    byte[] hashedValue = readHash(encodedToken);
    return Arrays.equals(asEncoded, hashedValue);
  }


  private static byte[] readSalt(String encodedToken) {
    return fromHex(encodedToken.split(":")[SALT_INDEX]);
  }

  private static int readIterationCount(String encodedToken) {
    return Integer.parseInt(encodedToken.split(":")[ITERATION_COUNT_INDEX]);
  }

  private static byte[] readHash(String encodedToken) {
    return fromHex(encodedToken.split(":")[HASH_INDEX]);
  }

  /**
   * Converts a hexadecimal String representation to a byte array
   *
   * @param hex the hexadecimal String
   * @return the converted byte array
   * @since 1.0.0
   */
  private static byte[] fromHex(String hex) {
    byte[] binary = new byte[hex.length() / 2];
    for (int i = 0; i < binary.length; i++) {
      binary[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
    }
    return binary;
  }

  /**
   * Uses Password-Based Encryption to encrypt a token given a salt and iterations
   * @param token the token to be encrypted
   * @param salt the salt used in the encryption
   * @param iterationCount the number of iterations
   * @return encryption result
   */
  private static byte[] pbe(char[] token, byte[] salt, int iterationCount) {
    KeySpec spec =
        new PBEKeySpec(token, salt, iterationCount, ENCRYPTION_SETTINGS.keyBitSize());
    SecretKey secretKey;
    try {
      SecretKeyFactory result;
      result = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      SecretKeyFactory factory = result;
      secretKey = factory.generateSecret(spec);
    } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
      throw new RuntimeException("error encrypting token: " + e.getMessage());
    }
    return new SecretKeySpec(secretKey.getEncoded(), ENCRYPTION_SETTINGS.cipher()).getEncoded();
  }

}
