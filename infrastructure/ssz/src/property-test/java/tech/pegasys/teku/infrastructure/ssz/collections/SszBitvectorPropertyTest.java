/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import java.util.Random;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBitvectorPropertyTest {
  @Property
  void roundtripSsz(
      @ForAll final Random random, @ForAll @IntRange(min = 1, max = 1000) final int size) {
    final int[] bits =
        IntStream.range(0, size).sequential().filter(__ -> random.nextBoolean()).toArray();
    final SszBitvector original = SszBitvectorSchema.create(size).ofBits(bits);
    Bytes serialized = original.sszSerialize();
    SszBitvector deserialized = original.getSchema().sszDeserialize(serialized);
    assertThatIntCollection(deserialized.getAllSetBits()).isEqualTo(original.getAllSetBits());
    Assertions.assertThat(deserialized.size()).isEqualTo(original.size());
    for (int i = 0; i < original.size(); i++) {
      assertThat(deserialized.getBit(i)).isEqualTo(original.getBit(i));
    }
    SszDataAssert.assertThatSszData(deserialized).isEqualByAllMeansTo(original);
  }

  @Property
  void roundtripTree(
      @ForAll final Random random, @ForAll @IntRange(min = 1, max = 1000) final int size) {
    final int[] bits =
        IntStream.range(0, size).sequential().filter(__ -> random.nextBoolean()).toArray();
    final SszBitvector original = SszBitvectorSchema.create(size).ofBits(bits);
    TreeNode tree = original.getBackingNode();
    SszBitvector result = original.getSchema().createFromBackingNode(tree);
    assertThatIntCollection(result.getAllSetBits()).isEqualTo(original.getAllSetBits());
    Assertions.assertThat(result.size()).isEqualTo(original.size());
    for (int i = 0; i < result.size(); i++) {
      assertThat(result.getBit(i)).isEqualTo(original.getBit(i));
      Assertions.assertThat(result.get(i)).isEqualTo(original.get(i));
    }
    SszDataAssert.assertThatSszData(result).isEqualByAllMeansTo(original);
  }
}
