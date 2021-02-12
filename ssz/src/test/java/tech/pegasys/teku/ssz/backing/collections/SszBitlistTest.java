/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeUtil;

public class SszBitlistTest {

  static Random random = new Random();
  static SszBitlistSchema<SszBitlist> emptySchema = SszBitlistSchema.create(0);
  static SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(500);
  static SszBitlistSchema<SszBitlist> hugeSchema = SszBitlistSchema.create(1L << 62);

  static SszBitlist random(SszBitlistSchema<?> schema, int size) {
    return schema.ofBits(
        size, IntStream.range(0, size).filter(__ -> random.nextBoolean()).toArray());
  }

  static Stream<Arguments> bitlistArgs() {
    return Stream.of(
        Arguments.of(emptySchema.empty()),
        Arguments.of(schema.empty()),
        Arguments.of(hugeSchema.empty()),
        Arguments.of(random(schema, 1)),
        Arguments.of(random(hugeSchema, 1)),
        Arguments.of(random(schema, 2)),
        Arguments.of(random(hugeSchema, 2)),
        Arguments.of(random(schema, 254)),
        Arguments.of(random(hugeSchema, 254)),
        Arguments.of(random(schema, 255)),
        Arguments.of(random(hugeSchema, 255)),
        Arguments.of(random(schema, 256)),
        Arguments.of(random(hugeSchema, 256)),
        Arguments.of(random(schema, 257)),
        Arguments.of(random(hugeSchema, 257)),
        Arguments.of(random(hugeSchema, 511)),
        Arguments.of(random(hugeSchema, 512)),
        Arguments.of(random(hugeSchema, 513)),
        Arguments.of(random(hugeSchema, 10000)));
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testSszRoundtrip(SszBitlist bitlist1) {
    Bytes ssz1 = bitlist1.sszSerialize();
    SszBitlist bitlist2 = bitlist1.getSchema().sszDeserialize(ssz1);

    assertThat(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());

    Bytes ssz2 = bitlist2.sszSerialize();
    assertThat(ssz2).isEqualTo(ssz1);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testEqualOr(SszBitlist bitlist) {
    SszBitlist res = bitlist.or(bitlist);
    assertThat(res).isEqualTo(bitlist);
  }

  @Test
  void testEmptyHashTreeRoot() {
    assertThat(emptySchema.empty().hashTreeRoot())
        .isEqualTo(Hash.sha2_256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
    assertThat(schema.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(Bytes.concatenate(TreeUtil.ZERO_TREES[1].hashTreeRoot(), Bytes32.ZERO)));
    assertThat(hugeSchema.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha2_256(
                Bytes.concatenate(TreeUtil.ZERO_TREES[62 - 8].hashTreeRoot(), Bytes32.ZERO)));
  }
}
