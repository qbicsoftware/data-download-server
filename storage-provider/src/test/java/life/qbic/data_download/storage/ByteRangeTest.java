package life.qbic.data_download.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ByteRangeTest {

  @Test
  @DisplayName("length is inclusive end - start + 1")
  void lengthIsInclusive() {
    assertEquals(100, new ByteRange(0, 99).length());
    assertEquals(1, new ByteRange(5, 5).length());
  }

  @Test
  @DisplayName("negative start is rejected")
  void negativeStartIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ByteRange(-1, 10));
  }

  @Test
  @DisplayName("end smaller than start is rejected")
  void endSmallerThanStartIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ByteRange(10, 9));
  }
}