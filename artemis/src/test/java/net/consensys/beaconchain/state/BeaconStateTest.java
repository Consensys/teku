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

package net.consensys.artemis.state;

import static net.consensys.artemis.state.BeaconState.BeaconStateHelperFunctions.bytes3ToInt;
import static net.consensys.artemis.state.BeaconState.BeaconStateHelperFunctions.clamp;
import static net.consensys.artemis.state.BeaconState.BeaconStateHelperFunctions.intToBytes3;
import static net.consensys.artemis.state.BeaconState.BeaconStateHelperFunctions.shuffle;
import static net.consensys.artemis.state.BeaconState.BeaconStateHelperFunctions.split;
import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.artemis.ethereum.core.Hash;
import net.consensys.artemis.util.bytes.Bytes3;
import net.consensys.artemis.util.bytes.BytesValue;

import org.junit.Test;

public class BeaconStateTest {

  private Hash hashSrc() {
    BytesValue bytes = BytesValue.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    return Hash.hash(bytes);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentIntToBytes3() {
    intToBytes3(-1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentsBytes3ToInt() {
    bytes3ToInt(hashSrc(), -1);
  }

  @Test
  public void convertIntToBytes3() {
    Bytes3 expected = Bytes3.wrap(new byte[]{(byte) 1, (byte) 256, (byte) 65656});
    Bytes3 actual = intToBytes3(65656);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void convertBytes3ToInt() {
    int expected = 817593;
    int actual = bytes3ToInt(hashSrc(), 0);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testShuffle() {
    Object[] actual = shuffle(new Object[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, hashSrc());
    Object[] expected = {2, 4, 10, 7, 5, 6, 9, 8, 1, 3};
    assertThat(actual).isEqualTo(expected);
  }

  @Test(expected = IllegalArgumentException.class)
  public void failsWhenInvalidArgumentTestSplit() {
    split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7}, -1);
  }

  @Test
  public void splitReturnsOneSmallerSizedSplit() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7}, 3);
    Object[][] expected = {{0, 1}, {2, 3, 4}, {5, 6, 7}};
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsTwoSmallerSizedSplits() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6}, 3);
    Object[][] expected = {{0, 1}, {2, 3}, {4, 5, 6}};
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void splitReturnsEquallySizedSplits() {
    Object[] actual = split(new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, 3);
    Object[][] expected = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}};
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsMinVal() {
    int actual = clamp(3, 5, 0);
    int expected = 3;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsMaxVal() {
    int actual = clamp(3, 5, 6);
    int expected = 5;
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void clampReturnsX() {
    int actual = clamp(3, 5, 4);
    int expected = 4;
    assertThat(actual).isEqualTo(expected);
  }

}
