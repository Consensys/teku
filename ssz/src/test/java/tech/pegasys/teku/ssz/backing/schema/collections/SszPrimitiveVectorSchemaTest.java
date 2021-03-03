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

package tech.pegasys.teku.ssz.backing.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.collections.SszPrimitiveVector;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;

public class SszPrimitiveVectorSchemaTest {

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
