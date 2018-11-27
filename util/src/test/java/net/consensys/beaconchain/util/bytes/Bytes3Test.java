package net.consensys.beaconchain.util.bytes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class Bytes3Test {

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArraySmallerThan3() {
    Bytes3.wrap(new byte[2]);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenWrappingArrayLargerThan3() {
    Bytes3.wrap(new byte[4]);
  }

  @Test
  public void leftPadAValueToBytes3() {
    Bytes3 b3 = Bytes3.leftPad(BytesValue.of(1, 2, 3));
    assertThat(b3.size()).isEqualTo(3);
    assertThat(b3.get(0)).isEqualTo((byte) 1);
    assertThat(b3.get(1)).isEqualTo((byte) 2);
    assertThat(b3.get(2)).isEqualTo((byte) 3);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenLeftPaddingValueLargerThan3() {
    Bytes3.leftPad(MutableBytesValue.create(4));
  }
}
