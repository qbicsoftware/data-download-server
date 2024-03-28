package life.qbic.data_download.rest.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class QBiCTokenMatcherTest {

  @ParameterizedTest
  @CsvSource({
      "43aZ3xka9613JC64F01cJA6wvS7qTO1a,100000:dc0fbdfb18b7bd1765660f50d4e60e401aa4ce29:9e67a05c631a0e87d78252b238268aa84fe2f72e9725c5667e7e3be05696b57f",
      "P32o4r65r60w62c9NA2ed7NwK74625F6,100000:ad83ad744ab13e89a55f92da5e42c1378f77d4a7:22c11194a8686c56d00c3ba819db9f14a9cf864d5c8d27a8a645c48e1c110100"
  })
  @DisplayName("The raw token can be compared to the encoded token")
  void theRawTokenCanBeComparedToTheEncodedToken(String rawToken, String encodedToken) {
    TokenMatcher qBiCTokenMatcher = new QBiCTokenMatcher();
    assertTrue(qBiCTokenMatcher.matches(rawToken.toCharArray(), encodedToken));
  }

}
