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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.ssz.backing.TestContainers.TestByteVectorContainer;
import tech.pegasys.teku.ssz.backing.TestContainers.TestContainer;
import tech.pegasys.teku.ssz.backing.view.AbstractSszImmutableContainer;

public class AbstractSszImmutableContainerTest extends SszContainerTest {

  @Override
  public Stream<SszContainer> sszData() {
    return Stream.of(
        TestContainer.SSZ_SCHEMA.getDefault(), TestByteVectorContainer.SSZ_SCHEMA.getDefault());
  }

  @MethodSource("sszDataArguments")
  @ParameterizedTest
  void createWritableCopy_throwsUnsupportedOperation(AbstractSszImmutableContainer container) {
    assertThatThrownBy(container::createWritableCopy)
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
