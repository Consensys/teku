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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public interface SszCollectionTestBase extends SszCompositeTestBase {

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
    C newElement = data.getSchema().getElementSchema().getDefault();
    assertThatThrownBy(() -> list.add(newElement))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
