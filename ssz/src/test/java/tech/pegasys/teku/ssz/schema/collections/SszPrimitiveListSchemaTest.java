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

package tech.pegasys.teku.ssz.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.impl.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;

public class SszPrimitiveListSchemaTest implements SszListSchemaTestBase {

  @Override
  public Stream<SszPrimitiveListSchema<?, ?, ?>> testSchemas() {
    return Stream.of(
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 31),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 32),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 33),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 63),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 64),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, 65),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 7),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 8),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 9),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 15),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 16),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES4_SCHEMA, 17),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 2),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 3),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 4),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 5),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 0),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 1),
        SszPrimitiveListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10));
  }

  @Test
  void sanityTest() {
    SszPrimitiveVectorSchema<UInt64, SszUInt64, ?> schema =
        SszPrimitiveVectorSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 10);
    SszPrimitiveVector<UInt64, SszUInt64> defaultVector = schema.getDefault();
    assertThat(defaultVector).hasSize(10).containsOnly(SszUInt64.of(UInt64.ZERO));
    assertThat(defaultVector.asListUnboxed()).hasSize(10).containsOnly(UInt64.ZERO);

    List<UInt64> uints =
        LongStream.range(1, 11).mapToObj(UInt64::valueOf).collect(Collectors.toList());
    SszPrimitiveVector<UInt64, SszUInt64> vector1 = schema.of(uints);
    assertThat(vector1.asListUnboxed()).hasSize(10).containsSequence(uints);
  }
}
