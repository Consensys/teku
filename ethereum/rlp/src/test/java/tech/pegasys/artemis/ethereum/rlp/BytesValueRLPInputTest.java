/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.ethereum.rlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import tech.pegasys.artemis.util.bytes.BytesValue;

import org.junit.Test;

public class BytesValueRLPInputTest {

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
    RLPInput in = RLP.input(BytesValue.EMPTY);
    assertTrue(in.isDone());
  }

  @Test
  public void singleByte() {
    RLPInput in = RLP.input(h("0x01"));
    assertFalse(in.isDone());
    assertEquals((byte) 1, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleShortElement() {
    RLPInput in = RLP.input(h("0x81FF"));
    assertFalse(in.isDone());
    assertEquals((byte) 0xFF, in.readByte());
    assertTrue(in.isDone());
  }

  @Test
  public void singleBarelyShortElement() {
    RLPInput in = RLP.input(h("0xb7" + times("2b", 55)));
    assertFalse(in.isDone());
    assertEquals(h(times("2b", 55)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleBarelyLongElement() {
    RLPInput in = RLP.input(h("0xb838" + times("2b", 56)));
    assertFalse(in.isDone());
    assertEquals(h(times("2b", 56)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void singleLongElement() {
    RLPInput in = RLP.input(h("0xb908c1" + times("3c", 2241)));

    assertFalse(in.isDone());
    assertEquals(h(times("3c", 2241)), in.readBytesValue());
    assertTrue(in.isDone());
  }

  @Test
  public void assertLongScalar() {
    // Scalar should be encoded as the minimal byte array representing the number. For 0, that means
    // the empty byte array, which is a short element of zero-length, so 0x80.
    assertLongScalar(0L, h("0x80"));

    assertLongScalar(1L, h("0x01"));
    assertLongScalar(15L, h("0x0F"));
    assertLongScalar(1024L, h("0x820400"));
  }

  @Test(expected = RLPException.class)
  public void longScalar_NegativeLong() {
    assertLongScalar(-1L, h("0xFFFFFFFFFFFFFFFF"));
  }

  private void assertLongScalar(long expected, BytesValue toTest) {
    RLPInput in = RLP.input(toTest);
    assertFalse(in.isDone());
    assertEquals(expected, in.readLongScalar());
    assertTrue(in.isDone());
  }

  @Test
  public void intScalar() {
    // Scalar should be encoded as the minimal byte array representing the number. For 0, that means
    // the empty byte array, which is a short element of zero-length, so 0x80.
    assertIntScalar(0, h("0x80"));

    assertIntScalar(1, h("0x01"));
    assertIntScalar(15, h("0x0F"));
    assertIntScalar(1024, h("0x820400"));
  }

  private void assertIntScalar(int expected, BytesValue toTest) {
    RLPInput in = RLP.input(toTest);
    assertFalse(in.isDone());
    assertEquals(expected, in.readIntScalar());
    assertTrue(in.isDone());
  }

  @Test
  public void emptyList() {
    RLPInput in = RLP.input(h("0xc0"));
    assertFalse(in.isDone());
    assertEquals(0, in.enterList());
    assertFalse(in.isDone());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleShortList() {
    RLPInput in = RLP.input(h("0xc22c3b"));

    assertFalse(in.isDone());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleIntBeforeShortList() {
    RLPInput in = RLP.input(h("0x02c22c3b"));

    assertFalse(in.isDone());
    assertEquals(2, in.readIntScalar());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void simpleNestedList() {
    RLPInput in = RLP.input(h("0xc52cc203123b"));

    assertFalse(in.isDone());
    assertEquals(3, in.enterList());
    assertEquals((byte) 0x2c, in.readByte());
    assertEquals(2, in.enterList());
    assertEquals((byte) 0x03, in.readByte());
    assertEquals((byte) 0x12, in.readByte());
    in.leaveList();
    assertEquals((byte) 0x3b, in.readByte());
    in.leaveList();
    assertTrue(in.isDone());
  }

  @Test
  public void readAsRlp() {
    // Test null value
    BytesValue nullValue = h("0x80");
    RLPInput nv = RLP.input(nullValue);
    assertEquals(nv.raw(), nv.readAsRlp().raw());
    nv.reset();
    assertTrue(nv.nextIsNull());
    assertTrue(nv.readAsRlp().nextIsNull());

    // Test empty list
    BytesValue emptyList = h("0xc0");
    RLPInput el = RLP.input(emptyList);
    assertEquals(emptyList, el.readAsRlp().raw());
    el.reset();
    assertEquals(0, el.readAsRlp().enterList());
    el.reset();
    assertEquals(0, el.enterList());

    BytesValue nestedList = RLP.encode(out -> {
      out.startList();
      out.writeByte((byte) 0x01);
      out.writeByte((byte) 0x02);
      out.startList();
      out.writeByte((byte) 0x11);
      out.writeByte((byte) 0x12);
      out.startList();
      out.writeByte((byte) 0x21);
      out.writeByte((byte) 0x22);
      out.endList();
      out.endList();
      out.endList();
    });

    RLPInput nl = RLP.input(nestedList);
    RLPInput compare = nl.readAsRlp();
    assertEquals(nl.raw(), compare.raw());
    nl.reset();
    nl.enterList();
    nl.skipNext(); // 0x01

    // Read the next byte that's inside the list, extract it as raw RLP and assert it's its own representation.
    assertEquals(h("0x02"), nl.readAsRlp().raw());
    // Extract the inner list.
    assertEquals(h("0xc51112c22122"), nl.readAsRlp().raw());
    // Reset
    nl.reset();
    nl.enterList();
    nl.skipNext();
    nl.skipNext();
    nl.enterList();
    nl.skipNext();
    nl.skipNext();

    // Assert on the inner list of depth 3.
    assertEquals(h("0xc22122"), nl.readAsRlp().raw());
  }

  @Test
  public void raw() {
    BytesValue initial = h("0xc80102c51112c22122");
    RLPInput in = RLP.input(initial);
    assertEquals(initial, in.raw());
  }

  @Test
  public void reset() {
    RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    for (int i = 0; i < 100; i++) {
      assertEquals(3, in.enterList());
      assertEquals(0x01, in.readByte());
      assertEquals(0x02, in.readByte());
      assertEquals(3, in.enterList());
      assertEquals(0x11, in.readByte());
      assertEquals(0x12, in.readByte());
      assertEquals(2, in.enterList());
      assertEquals(0x21, in.readByte());
      assertEquals(0x22, in.readByte());
      in.reset();
    }
  }

  @Test
  public void ignoreListTail() {
    RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    assertEquals(3, in.enterList());
    assertEquals(0x01, in.readByte());
    in.leaveList(true);
  }

  @Test(expected = RLPException.class)
  public void leaveListEarly() {
    RLPInput in = RLP.input(h("0xc80102c51112c22122"));
    assertEquals(3, in.enterList());
    assertEquals(0x01, in.readByte());
    in.leaveList(false);
  }
}
