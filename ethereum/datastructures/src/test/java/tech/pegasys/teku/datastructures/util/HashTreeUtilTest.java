/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.datastructures.util.HashTreeUtil.is_power_of_two;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

/** Tests around the computation of a hash tree root using SSZ and merkleization. */
class HashTreeUtilTest {

  @Test
  void isPowerOfTwoTest() {
    // Powers of two
    assertTrue(is_power_of_two(1));
    assertTrue(is_power_of_two(2));
    assertTrue(is_power_of_two(512));
    assertTrue(is_power_of_two(0x040000));

    // Not powers of two
    assertFalse(is_power_of_two(0));
    assertFalse(is_power_of_two(42));
    assertFalse(is_power_of_two(Integer.MAX_VALUE));

    // Negative numbers
    assertFalse(is_power_of_two(-1));
    assertFalse(is_power_of_two(-16));
    assertFalse(is_power_of_two(Integer.MIN_VALUE));
  }

  @Test
  void shouldHashTreeRootCorrectly() {
    final Bytes input =
        Bytes.fromHexString(
            "0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4BF2D153F649F7B53359FE8B94A38E44C");
    final Bytes expected =
        Bytes.fromHexString("0x89E40BFF069E391CA393901DA3287BBE35DC429265927ABE3C3F06BEC8E0B9CD");
    final Bytes32 actual =
        HashTreeUtil.hash_tree_root(HashTreeUtil.SSZTypes.VECTOR_OF_BASIC, input);
    assertEquals(expected, actual);
  }

  @Test
  void testPack() {
    final Bytes input =
        Bytes.fromHexString(
            "0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4BF2D153F649F7B53359FE8B94A38E44C");
    final List<Bytes32> result =
        Arrays.asList(
            Bytes32.fromHexString(
                "0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4"),
            Bytes32.fromHexString(
                "0xBF2D153F649F7B53359FE8B94A38E44C00000000000000000000000000000000"));
    assertEquals(result, HashTreeUtil.separateIntoChunks(input));
  }

  @Test
  void testMerkleize() {
    final List<Bytes32> input =
        Arrays.asList(
            Bytes32.fromHexString(
                "0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4"),
            Bytes32.fromHexString(
                "0xBF2D153F649F7B53359FE8B94A38E44C00000000000000000000000000000000"));

    final Bytes expected =
        Bytes.fromHexString("0x89E40BFF069E391CA393901DA3287BBE35DC429265927ABE3C3F06BEC8E0B9CD");
    assertEquals(expected, HashTreeUtil.merkleize(input));
  }

  @Test
  void testMerge() {
    List<Bytes32> result = new ArrayList<>(2);
    result.add(null);
    result.add(null);
    HashTreeUtil.merge(
        Bytes32.fromHexString("0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4"),
        0,
        result,
        2,
        1);
    assertEquals(
        Arrays.asList(
            Bytes.fromHexString(
                "0xA99A76ED7796F7BE22D5B7E85DEEB7C5677E88E511E0B337618F8C4EB61349B4"),
            null),
        result);
  }
}
