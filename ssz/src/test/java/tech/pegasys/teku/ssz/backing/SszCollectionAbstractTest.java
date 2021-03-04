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

package tech.pegasys.teku.ssz.backing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ssz.backing.SszDataAssert.assertThatSszData;

import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.TestContainers.TestByteVectorContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.TestSmallContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.VariableSizeContainer;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;

public interface SszCollectionAbstractTest extends SszCompositeAbstractTest {

  static Stream<SszSchema<?>> elementSchemas() {
    return Stream.of(
        SszPrimitiveSchemas.BIT_SCHEMA,
        SszPrimitiveSchemas.BYTE_SCHEMA,
        SszPrimitiveSchemas.BYTES4_SCHEMA,
        SszPrimitiveSchemas.UINT64_SCHEMA,
        SszPrimitiveSchemas.BYTES32_SCHEMA,
        TestSmallContainer.SSZ_SCHEMA,
        TestByteVectorContainer.SSZ_SCHEMA,
        VariableSizeContainer.SSZ_SCHEMA);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default void size_matchesReturnedData(SszCollection<?> data) {
    assertThat(data.asList()).hasSize(data.size());
    assertThat(data.stream()).hasSize(data.size());
    assertThat(data).hasSize(data.size());
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <C extends SszData> void stream_returnsSameData(SszCollection<C> data) {
    assertThat(data.stream()).containsExactlyElementsOf(data);
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <C extends SszData> void asList_returnsSameData(SszCollection<C> data) {
    List<C> list = data.asList();
    assertThat(list.size()).isEqualTo(data.size());
    for (int i = 0; i < list.size(); i++) {
      assertThatSszData(list.get(i)).isEqualByAllMeansTo(data.get(i));
    }
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  default <C extends SszData> void asList_isUnmodifiable(SszCollection<C> data) {
    List<C> list = data.asList();
    assertThatThrownBy(() -> list.set(0, data.get(0))).isInstanceOf(UnsupportedOperationException.class);
  }
}
