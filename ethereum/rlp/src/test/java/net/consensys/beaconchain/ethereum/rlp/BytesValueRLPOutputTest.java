package net.consensys.beaconchain.ethereum.rlp;

import static org.junit.Assert.assertEquals;

import net.consensys.beaconchain.util.bytes.BytesValue;

import org.junit.Test;

public class BytesValueRLPOutputTest {

  private static BytesValue h(String hex) {
    return BytesValue.fromHexString(hex);
  }

  private static String times(String base, int times) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < times; i++)
      sb.append(base);
    return sb.toString();
  }

  @Test
  public void empty() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    assertEquals(BytesValue.EMPTY, out.encoded());
  }

  @Test
  public void singleByte() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeByte((byte) 1);

    // Single byte should be encoded as itself
    assertEquals(h("0x01"), out.encoded());
  }

  @Test
  public void singleShortElement() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeByte((byte) 0xFF);

    // Bigger than single byte: 0x80 + length then value, where length is 1.
    assertEquals(h("0x81FF"), out.encoded());
  }

  @Test
  public void singleBarelyShortElement() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytesValue(h(times("2b", 55)));

    // 55 bytes, so still short: 0x80 + length then value, where length is 55.
    assertEquals(h("0xb7" + times("2b", 55)), out.encoded());
  }

  @Test
  public void singleBarelyLongElement() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytesValue(h(times("2b", 56)));

    // 56 bytes, so long element: 0xb7 + length of value size + value, where the value size is 56.
    // 56 is 0x38 so its size is 1 byte.
    assertEquals(h("0xb838" + times("2b", 56)), out.encoded());
  }

  @Test
  public void singleLongElement() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeBytesValue(h(times("3c", 2241)));

    // 2241 bytes, so long element: 0xb7 + length of value size + value, where the value size is 2241,
    // 2241 is 0x8c1 so its size is 2 bytes.
    assertEquals(h("0xb908c1" + times("3c", 2241)), out.encoded());
  }

  @Test(expected = IllegalStateException.class)
  public void multipleElementAddedWithoutList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeByte((byte) 0);
    out.writeByte((byte) 1);
  }

  @Test
  public void longScalar() {
    // Scalar should be encoded as the minimal byte array representing the number. For 0, that means
    // the empty byte array, which is a short element of zero-length, so 0x80.
    assertLongScalar(h("0x80"), 0);

    assertLongScalar(h("0x01"), 1);
    assertLongScalar(h("0x0F"), 15);
    assertLongScalar(h("0x820400"), 1024);
  }

  private void assertLongScalar(BytesValue expected, long toTest) {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.writeLongScalar(toTest);
    assertEquals(expected, out.encoded());
  }

  @Test
  public void emptyList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.endList();

    assertEquals(h("0xc0"), out.encoded());
  }

  @Test(expected = IllegalStateException.class)
  public void unclosedList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.encoded();
  }

  @Test(expected = IllegalStateException.class)
  public void closeUnopenedList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.endList();
  }

  @Test
  public void simpleShortList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeByte((byte) 0x2c);
    out.writeByte((byte) 0x3b);
    out.endList();

    // List with payload size = 2 (both element are single bytes)
    // so 0xc0 + size then payloads
    assertEquals(h("0xc22c3b"), out.encoded());
  }

  @Test
  public void simpleNestedList() {
    BytesValueRLPOutput out = new BytesValueRLPOutput();
    out.startList();
    out.writeByte((byte) 0x2c);
    // Nested list has 2 simple elements, so will be 0xc20312
    out.startList();
    out.writeByte((byte) 0x03);
    out.writeByte((byte) 0x12);
    out.endList();
    out.writeByte((byte) 0x3b);
    out.endList();

    // List payload size = 5 (2 single bytes element + nested list of size 3)
    // so 0xc0 + size then payloads
    assertEquals(h("0xc52cc203123b"), out.encoded());
  }
}
