/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MilestoneBasedFetchBlockTaskFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(UInt64.ONE);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ZERO);

  @SuppressWarnings("unchecked")
  private final P2PNetwork<Eth2Peer> eth2Network = mock(P2PNetwork.class);

  private final MilestoneBasedFetchBlockTaskFactory milestoneBasedFetchBlockTaskFactory =
      new MilestoneBasedFetchBlockTaskFactory(spec, eth2Network);

  @Test
  public void createsFetchBlockTaskForMilestonesBeforeDeneb() {
    final FetchBlockTask task =
        milestoneBasedFetchBlockTaskFactory.create(UInt64.ONE, dataStructureUtil.randomBytes32());

    assertThat(task).isExactlyInstanceOf(FetchBlockTask.class);
  }

  @Test
  public void createsFetchBlockAndBlobsSidecarTaskForMilestonesAfterDeneb() {
    final FetchBlockTask task =
        milestoneBasedFetchBlockTaskFactory.create(
            UInt64.ONE.plus(slotsPerEpoch), dataStructureUtil.randomBytes32());

    assertThat(task).isExactlyInstanceOf(FetchBlockAndBlobsSidecarTask.class);
  }
}
