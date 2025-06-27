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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImplTest {
  @Test
  public void
      testDefaultFuluConfigWithoutValidatorsSamplingColumnsShouldContainsAllCustodyColumns() {
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            builder ->
                builder.fuluBuilder(
                    fuluBuilder ->
                        fuluBuilder
                            .dataColumnSidecarSubnetCount(4)
                            .numberOfColumns(128)
                            .numberOfCustodyGroups(128)
                            .custodyRequirement(4)
                            .samplesPerSlot(8)
                            .validatorCustodyRequirement(8)
                            .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                            .minEpochsForDataColumnSidecarsRequests(64)));

    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
    final CustodyGroupCountChannel custodyGroupCountChannel = mock(CustodyGroupCountChannel.class);
    final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);
    final MetricsSystem metricsSystem = new NoOpMetricsSystem();
    final CustodyGroupCountManagerImpl custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            specConfigFulu,
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            4,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    final List<UInt64> samplingColumnIndices = custodyGroupCountManager.getSamplingColumnIndices();
    // Sampling column groups should always include all custody columns at the minimum.
    assertThat(samplingColumnIndices)
        .contains(custodyGroupCountManager.getCustodyColumnIndices().toArray(new UInt64[] {}));
    assertEquals(8, samplingColumnIndices.size());
  }
}
