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

package tech.pegasys.teku.ssz.backing.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.RandomSszDataGenerator;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.tree.LeafNode;

public class SszPrimitiveSchemaTest implements SszSchemaTestBase {

  private final RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @Override
  public Stream<SszPrimitiveSchema<?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveSchemas.BIT_SCHEMA,
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SszPrimitiveSchemas.UINT64_SCHEMA,
        SszPrimitiveSchemas.BYTES4_SCHEMA,
        SszPrimitiveSchemas.BYTES32_SCHEMA);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void isPrimitive_shouldReturnTrue(SszPrimitiveSchema<?, ?> schema) {
    assertThat(schema.isPrimitive()).isTrue();
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  void getDefaultTree_shouldReturnLeaf(SszPrimitiveSchema<?, ?> schema) {
    assertThat(schema.getDefaultTree()).isInstanceOf(LeafNode.class);
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  <V, SszV extends SszPrimitive<V, SszV>> void boxed_roundtrip(SszPrimitiveSchema<V, SszV> schema) {
    SszV d = randomSsz.randomData(schema);
    V v = d.get();
    SszV d1 = schema.boxed(v);

    assertThatSszData(d1).isEqualByAllMeansTo(d);

    V v1 = d1.get();

    assertThat(v1).isEqualTo(v);
  }

  @Test
  void getBitsSize_shouldReturnCorrectValue() {
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.getBitsSize()).isEqualTo(1);
    assertThat(SszPrimitiveSchemas.BYTE_SCHEMA.getBitsSize()).isEqualTo(8);
    assertThat(SszPrimitiveSchemas.UINT64_SCHEMA.getBitsSize()).isEqualTo(64);
    assertThat(SszPrimitiveSchemas.BYTES4_SCHEMA.getBitsSize()).isEqualTo(32);
    assertThat(SszPrimitiveSchemas.BYTES32_SCHEMA.getBitsSize()).isEqualTo(256);
  }
}
