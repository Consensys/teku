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

package tech.pegasys.teku.ssz.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SszDataAssert;
import tech.pegasys.teku.ssz.SszPrimitive;
import tech.pegasys.teku.ssz.primitive.SszBit;
import tech.pegasys.teku.ssz.primitive.SszUInt256;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.ssz.tree.LeafNode;

public class SszPrimitiveSchemaTest extends SszSchemaTestBase {

  @Override
  public Stream<SszPrimitiveSchema<?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveSchemas.BIT_SCHEMA,
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SszPrimitiveSchemas.UINT64_SCHEMA,
        SszPrimitiveSchemas.UINT256_SCHEMA,
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

    SszDataAssert.assertThatSszData(d1).isEqualByAllMeansTo(d);

    V v1 = d1.get();

    assertThat(v1).isEqualTo(v);
  }

  @Test
  void getBitsSize_shouldReturnCorrectValue() {
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.getBitsSize()).isEqualTo(1);
    assertThat(SszPrimitiveSchemas.BYTE_SCHEMA.getBitsSize()).isEqualTo(8);
    assertThat(SszPrimitiveSchemas.UINT64_SCHEMA.getBitsSize()).isEqualTo(64);
    assertThat(SszPrimitiveSchemas.UINT256_SCHEMA.getBitsSize()).isEqualTo(256);
    assertThat(SszPrimitiveSchemas.BYTES4_SCHEMA.getBitsSize()).isEqualTo(32);
    assertThat(SszPrimitiveSchemas.BYTES32_SCHEMA.getBitsSize()).isEqualTo(256);
  }

  @Test
  void sszDeserializeTree_shouldRejectValuesPaddedWithNonZero() {
    assertThatThrownBy(
            () -> SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0xda")))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void sszDeserializeTree_shouldAcceptValuesPaddedWithZero() {
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0x01")))
        .isSameAs(SszBit.of(true));
    assertThat(SszPrimitiveSchemas.BIT_SCHEMA.sszDeserialize(Bytes.fromHexString("0x00")))
        .isSameAs(SszBit.of(false));
  }

  @Test
  void SszUInt64_shouldBeStoredInLE() {
    byte[] input = new byte[8];

    IntStream.range(0, input.length).forEach(i -> input[i] = (byte) i);

    Bytes wrappedInput = Bytes.wrap(input);

    UInt64 inputUint64 = UInt64.fromLongBits(wrappedInput.toLong());

    SszUInt64 sszUInt64 = SszUInt64.of(inputUint64);

    Bytes representation = sszUInt64.getBackingNode().hashTreeRoot().reverse().slice(24);

    assertThat(wrappedInput).isEqualByComparingTo(representation);
  }

  @Test
  void SszUInt256_shouldBeStoredInLE() {
    byte[] input = new byte[32];

    IntStream.range(0, input.length).forEach(i -> input[i] = (byte) i);

    Bytes wrappedInput = Bytes.wrap(input);

    UInt256 inputUInt256 = UInt256.fromBytes(wrappedInput);

    SszUInt256 sszUInt256 = SszUInt256.of(inputUInt256);

    Bytes representation = sszUInt256.getBackingNode().hashTreeRoot().reverse();

    assertThat(wrappedInput).isEqualByComparingTo(representation);
  }
}
