/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;

class NodeDataProviderTest {

  private AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);

  @Test
  void getAttestationPoolSize_shouldAttestationPoolSize() {
    final NodeDataProvider nodeData = new NodeDataProvider(attestationPool);
    final int size = 123;
    when(attestationPool.getSize()).thenReturn(size);

    assertThat(nodeData.getAttestationPoolSize()).isEqualTo(size);
  }
}
