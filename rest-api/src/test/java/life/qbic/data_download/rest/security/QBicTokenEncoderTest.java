package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QBicTokenEncoderTest {

  final int validIterationCount = 100_000;
  final String validSalt = "010203040506070809000a0b0c0d0e0f";


  @ParameterizedTest
  @ValueSource(strings = {
      "test",
      "0018928349298347979234",
      "alsdkfj2983749234lkjwer0"
  })
  @DisplayName("encoded tokens are different than raw input")
  void encodedTokensAreDifferentThanRawInput(String input) {
    TokenEncoder classUnderTest = new QBicTokenEncoder(validSalt, validIterationCount);
    String encoded = classUnderTest.encode(input);
    assertNotEquals(input, encoded);
  }

  @ParameterizedTest
  @CsvSource({
      "test1234,test2345",
      "02304980928340,02304980928341"
  })
  @DisplayName("different tokens lead to different encoded tokens")
  void differentTokensLeadToDifferentEncodedTokens(String tokenOne, String tokenTwo) {
    TokenEncoder classUnderTest = new QBicTokenEncoder(validSalt, validIterationCount);
    String encodedOne = classUnderTest.encode(tokenOne);
    String encodedTwo = classUnderTest.encode(tokenTwo);

    assertNotEquals(encodedOne, encodedTwo);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "wlekrjwoweirwnwelrkjklwjer",
      "023948092349023804980982340982034",
      "ksjdhskjfkwernmq293847102398",
      "1",
      "a"
  })
  @DisplayName("identical tokens lead to the same encoded tokens")
  void identicalTokensLeadToTheSameEncodedTokens(String token) {
    TokenEncoder classUnderTest = new QBicTokenEncoder(validSalt, validIterationCount);
    String encodedOne = classUnderTest.encode(token);
    String encodedTwo = classUnderTest.encode(token);

    assertEquals(encodedOne, encodedTwo);
  }

  @ParameterizedTest
  @ValueSource(ints = {
      0, 1, 2, 4, 7, 12, 14, 15
  })
  @DisplayName("when the salt has less than 128 bits then fail")
  void whenTheSaltHasLessThan128BitsThenFail(int saltByteNumber) {
    var salt = generateRandomSalt(saltByteNumber);
    assertThrows(IllegalArgumentException.class,
        () -> new QBicTokenEncoder(salt, validIterationCount));
  }

  @Test
  @DisplayName("when the salt has 128 bits succeed")
  void whenTheSaltHas128BitsSucceed() {
    String salt = generateRandomSalt(16);
    var underTest = new QBicTokenEncoder(salt, validIterationCount);
    assertDoesNotThrow(() -> underTest.encode("myToken"));
  }

  @ParameterizedTest
  @ValueSource(ints = {
      16, 17, 18, 19, 77, 200
  })
  @DisplayName("when the salt has more than 128 bits succeed")
  void whenTheSaltHasMoreThan128BitsSucceed(int saltByteNumber) {
    String salt = generateRandomSalt(saltByteNumber);
    var underTest = new QBicTokenEncoder(salt, validIterationCount);
    assertDoesNotThrow(() -> underTest.encode("myToken"));
  }

  @ParameterizedTest
  @ValueSource(ints = {
      0,1,2,7,9,42,50,100,1_000,99_998, 99_999
  })
  @DisplayName("when the iteration count is below 100_000 then fail")
  void whenTheIterationCountIsBelow100000ThenFail(int iterationCount) {
    assertThrows(IllegalArgumentException.class,
        () -> new QBicTokenEncoder(validSalt, iterationCount));
  }

  @Test
  @DisplayName("when the iteration count is 100_000 then succeed")
  void whenTheIterationCountIs100000ThenSucceed() {
    assertDoesNotThrow(() -> {
      var tokenEncoder = new QBicTokenEncoder(validSalt, 100_000);
      tokenEncoder.encode("myToken");
    });
  }

  @ParameterizedTest
  @ValueSource(ints = {
      100_001, 100_002, 150_000
  })  @DisplayName("when the iteration count is higher than 100_000 then succeed")
  void whenTheIterationCountIsHigherThan100000ThenSucceed(int iterationCount) {
   assertDoesNotThrow(() -> {
     QBicTokenEncoder qBicTokenEncoder = new QBicTokenEncoder(validSalt, iterationCount);
     qBicTokenEncoder.encode("myToken");
   });
  }

  String generateRandomSalt(int byteCount) {
    var random = new SecureRandom();
    byte[] bytes = new byte[byteCount];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

}
