/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.RecentChainData;

class StatusMessageFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private StatusMessageFactory statusMessageFactory;

  @BeforeEach
  public void setUp() {
    when(recentChainData.getCurrentForkDigest())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes4()));
    when(recentChainData.getFinalizedCheckpoint())
        .thenReturn(Optional.of(dataStructureUtil.randomCheckpoint()));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(recentChainData.getChainHead())
        .thenAnswer(__ -> Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(0))));

    statusMessageFactory = new StatusMessageFactory(spec, recentChainData);
  }

  @Test
  public void createStatusMessageShouldReturnRpcBodySelectorForAllSupportedVersions() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    assertThat(rpcRequestBodySelector.getBody().apply("/eth2/beacon_chain/req/status/1/ssz_snappy"))
        .hasValueSatisfying(
            statusMessage -> assertThat(statusMessage).isInstanceOf(StatusMessagePhase0.class));
    assertThat(rpcRequestBodySelector.getBody().apply("/eth2/beacon_chain/req/status/2/ssz_snappy"))
        .hasValueSatisfying(
            statusMessage -> assertThat(statusMessage).isInstanceOf(StatusMessageFulu.class));
  }

  @Test
  public void createStatusMessageShouldReturnRpcBodySelectorThatFailsForUnsupportedVersion() {
    final RpcRequestBodySelector<StatusMessage> rpcRequestBodySelector =
        statusMessageFactory.createStatusMessage().orElseThrow();

    assertThatThrownBy(
            () ->
                rpcRequestBodySelector
                    .getBody()
                    .apply("/eth2/beacon_chain/req/status/3/ssz_snappy"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected protocol version: 3");
  }
}
