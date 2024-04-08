package life.qbic.data_download.rest.security;

import static java.util.Objects.requireNonNull;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A token encoder
 */
public class QBicTokenEncoder implements TokenEncoder {

  private static final int EXPECTED_MIN_SALT_BITS = 128;
  private static final int EXPECTED_MIN_SALT_BYTES = (int) Math.ceil(
      (double) EXPECTED_MIN_SALT_BITS / 8);
  private static final int EXPECTED_MIN_ITERATION_COUNT = 100_000;

  private final byte[] salt;
  private final int iterationCount;

  public QBicTokenEncoder(String salt, int iterationCount) {
    this.salt = fromHex(requireNonNull(salt, "salt must not be null"));
    if (this.salt.length < EXPECTED_MIN_SALT_BYTES) {
      throw new IllegalArgumentException(
          "salt must have at least " + EXPECTED_MIN_SALT_BITS + " bits.");
    }
    if (iterationCount < EXPECTED_MIN_ITERATION_COUNT) {
      throw new IllegalArgumentException(
          "Iteration count n=" + iterationCount + " cannot be less than n="
              + EXPECTED_MIN_ITERATION_COUNT);
    }
    this.iterationCount = iterationCount;
  }

  @Override
  public String encode(String token) {
    byte[] hash = pbe(token.toCharArray(), salt, iterationCount);
    return iterationCount + ":" + toHex(salt) + ":" + toHex(hash);
  }

  private record EncryptionSettings(String cipher, int keyBitSize) {
  }

  private static final EncryptionSettings ENCRYPTION_SETTINGS = new EncryptionSettings(
      "AES",
      256
  );

  private static byte[] fromHex(String hex) {
    return HexFormat.of().parseHex(hex);
  }

  private static String toHex(byte[] bytes) {
    HexFormat hexFormat = HexFormat.of();
    return hexFormat.formatHex(bytes);
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
