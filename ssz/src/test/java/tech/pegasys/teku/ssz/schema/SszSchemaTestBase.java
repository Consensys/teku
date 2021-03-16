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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.RandomSszDataGenerator;
import tech.pegasys.teku.ssz.SszData;
import tech.pegasys.teku.ssz.SszDataAssert;
import tech.pegasys.teku.ssz.sos.SimpleSszReader;
import tech.pegasys.teku.ssz.sos.SszDeserializeException;

public interface SszSchemaTestBase extends SszTypeTestBase {

  RandomSszDataGenerator randomSsz = new RandomSszDataGenerator();

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  default void getDefaultTree_shouldBeEqualToDefaultStructure(SszSchema<SszData> schema) {
    SszData defaultTreeData = schema.createFromBackingNode(schema.getDefaultTree());
    SszDataAssert.assertThatSszData(defaultTreeData).isEqualByAllMeansTo(schema.getDefault());
  }

  @MethodSource("testSchemaArguments")
  @ParameterizedTest
  default void sszDeserialize_tooLongSszShouldFailFastWithoutReadingWholeInput(
      SszSchema<SszData> schema) {

    long maxSszLength = schema.getSszLengthBounds().getMaxBytes();
    // ignore too large and degenerative structs
    Assumptions.assumeThat(maxSszLength).isLessThan(32 * 1024 * 1024).isGreaterThan(0);

    SszData data = randomSsz.randomData(schema);
    Bytes ssz = data.sszSerialize();

    Bytes sszWithExtraData = Bytes.wrap(ssz, Bytes.random((int) (maxSszLength - ssz.size() + 1)));
    AtomicInteger bytesCounter = new AtomicInteger();
    SimpleSszReader countingReader =
        new SimpleSszReader(sszWithExtraData) {
          @Override
          public Bytes read(int length) {
            bytesCounter.addAndGet(length);
            return super.read(length);
          }
        };
    assertThatThrownBy(() -> schema.sszDeserialize(countingReader))
        .isInstanceOf(SszDeserializeException.class);
    assertThat(bytesCounter.get()).isLessThanOrEqualTo(ssz.size());
  }
}
