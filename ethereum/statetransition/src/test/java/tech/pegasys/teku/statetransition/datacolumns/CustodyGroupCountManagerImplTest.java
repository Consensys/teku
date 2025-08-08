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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class CustodyGroupCountManagerImplTest {

  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final CustodyGroupCountChannel custodyGroupCountChannel =
      mock(CustodyGroupCountChannel.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private MiscHelpersFulu miscHelpersFulu;
  private Spec spec;
  private SpecConfigFulu specConfigFulu;
  private CustodyGroupCountManagerImpl custodyGroupCountManager;

  @Test
  public void defaultFuluConfigWithoutValidatorsSamplingColumnsShouldContainsAllCustodyColumns() {

    setUpManager(4, 8, 8);

    final List<UInt64> samplingColumnIndices = custodyGroupCountManager.getSamplingColumnIndices();
    // Sampling column groups should always include all custody columns at the minimum.
    assertThat(samplingColumnIndices)
        .containsAll(custodyGroupCountManager.getCustodyColumnIndices());
    assertEquals(8, samplingColumnIndices.size());
  }

  @Test
  public void onSlot_shouldUpdateCustodyAtGenesis() {
    setUpManager(4, 8, 8);

    custodyGroupCountManager.onSlot(UInt64.ZERO);

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(4);
    assertThat(custodyGroupCountManager.getCustodyGroupSyncedCount()).isZero();

    // prepare a validator
    when(proposersDataManager.getPreparedProposerInfo())
        .thenReturn(Map.of(UInt64.ZERO, mock(PreparedProposerInfo.class)));

    // make requirements go up
    when(miscHelpersFulu.getValidatorsCustodyRequirement(any(), anySet()))
        .thenReturn(UInt64.valueOf(10));

    custodyGroupCountManager.onSlot(UInt64.ONE);

    assertThat(custodyGroupCountManager.getCustodyGroupCount()).isEqualTo(10);
    assertThat(custodyGroupCountManager.getCustodyGroupSyncedCount()).isEqualTo(10);

    final List<UInt64> samplingColumnIndices = custodyGroupCountManager.getSamplingColumnIndices();

    assertThat(samplingColumnIndices)
        .containsAll(custodyGroupCountManager.getCustodyColumnIndices());
    assertEquals(10, samplingColumnIndices.size());
  }

  private void setUpManager(
      final int defaultCustodyRequirement,
      final int defaultSamplesPerSlot,
      final int defaultValidatorCustodyRequirement) {

    spec =
        TestSpecFactory.createMinimalFulu(
            builder ->
                builder.fuluBuilder(
                    fuluBuilder ->
                        fuluBuilder
                            .dataColumnSidecarSubnetCount(128)
                            .cellsPerExtBlob(128)
                            .numberOfColumns(128)
                            .numberOfCustodyGroups(128)
                            .custodyRequirement(defaultCustodyRequirement)
                            .samplesPerSlot(defaultSamplesPerSlot)
                            .validatorCustodyRequirement(defaultValidatorCustodyRequirement)
                            .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                            .minEpochsForDataColumnSidecarsRequests(64)));

    specConfigFulu = SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
    miscHelpersFulu =
        spy(MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers()));

    final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

    custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            spec,
            specConfigFulu,
            miscHelpersFulu,
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            defaultCustodyRequirement,
            dataStructureUtil.randomUInt256(),
            metricsSystem);

    when(combinedChainDataClient.getBestFinalizedState())
        .thenReturn(SafeFuture.completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));
  }
}
