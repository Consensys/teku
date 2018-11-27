package net.consensys.beaconchain.utils;

import org.junit.Test;

public class SimpleSerializeTest {

  /** Validate the serialization of an int */
  @Test
  public void intSerializeTest() {

    assertEquals(BytesValue.fromHexString(cowKeccak256), resultCow);
  }
}
