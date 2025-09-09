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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.CustodyGroupCountChannel;
import tech.pegasys.teku.statetransition.datacolumns.db.DataColumnSidecarDbAccessor;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DasSamplerBasicTest {
  private static final Logger LOG = LogManager.getLogger();
  static final Spec SPEC = TestSpecFactory.createMinimalFulu();

  private DataColumnSidecarCustody custody;
  private DataColumnSidecarRetriever retriever;
  private CurrentSlotProvider currentSlotProvider;
  private DataColumnSidecarDbAccessor db;
  static final SpecConfigFulu SPEC_CONFIG_FULU =
      SpecConfigFulu.required(SPEC.forMilestone(SpecMilestone.FULU).getConfig());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, SPEC);

  private ProposersDataManager proposersDataManager;
  private CustodyGroupCountChannel custodyGroupCountChannel;
  private CombinedChainDataClient combinedChainDataClient;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @BeforeEach
  public void setUp() {
    custody = mock(DataColumnSidecarCustody.class);
    retriever = mock(DataColumnSidecarRetriever.class);
    currentSlotProvider = mock(CurrentSlotProvider.class);
    db = mock(DataColumnSidecarDbAccessor.class);
    proposersDataManager = mock(ProposersDataManager.class);
    custodyGroupCountChannel = mock(CustodyGroupCountChannel.class);
    combinedChainDataClient = mock(CombinedChainDataClient.class);
  }

  @ParameterizedTest
  @MethodSource("testCheckDataAvailabilityArguments")
  public void testCheckDataAvailability_custodyRequirementNotValidating(
      final int configuredCustodyCount,
      final int validatorCount,
      final int expectedSampingRequests) {
    testCheckDataAvailability(configuredCustodyCount, validatorCount, expectedSampingRequests);
  }

  private static Object[][] testCheckDataAvailabilityArguments() {
    return new Object[][] {
      // With the default custody requirement and no validators active, we should sample max those
      // columns that are not in custody
      {
        SPEC_CONFIG_FULU.getCustodyRequirement(),
        0,
        SPEC_CONFIG_FULU.getSamplesPerSlot() - SPEC_CONFIG_FULU.getCustodyRequirement()
      },
      // For supernodes, no extra columns should be sampled
      {SPEC_CONFIG_FULU.getNumberOfCustodyGroups(), 0, 0},
      // For validating nodes, no extra columns should be sampled
      {SPEC_CONFIG_FULU.getCustodyRequirement(), 1, 0},
      {SPEC_CONFIG_FULU.getCustodyRequirement(), 2, 0},
      // For non validating nodes, where the user configures to custody one less that sample
      // requirement, it should retrieve that one column
      {SPEC_CONFIG_FULU.getSamplesPerSlot() - 1, 0, 1}
    };
  }

  public void testCheckDataAvailability(
      final int configuredCustodyCount,
      final int validatorCount,
      final int expectedSamplingRequests) {
    when(combinedChainDataClient.getCustodyGroupCount())
        .thenReturn(Optional.of(UInt64.valueOf(configuredCustodyCount)));
    final CustodyGroupCountManagerImpl custodyGroupCountManager =
        new CustodyGroupCountManagerImpl(
            SPEC,
            proposersDataManager,
            custodyGroupCountChannel,
            combinedChainDataClient,
            configuredCustodyCount,
            dataStructureUtil.randomUInt256(),
            metricsSystem);
    final DasSamplerBasic sampler =
        new DasSamplerBasic(
            SPEC, currentSlotProvider, db, custody, retriever, () -> custodyGroupCountManager);

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

    final Map<UInt64, PreparedProposerInfo> preparedProposerInfoMap =
        new HashMap<UInt64, PreparedProposerInfo>();
    final BeaconState beaconState = dataStructureUtil.randomBeaconState(validatorCount);
    for (int i = 0; i < validatorCount; i++) {
      preparedProposerInfoMap.put(
          UInt64.valueOf(i),
          new PreparedProposerInfo(UInt64.valueOf(1000), dataStructureUtil.randomEth1Address()));
    }
    when(combinedChainDataClient.getBestFinalizedState())
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconState)));
    when(proposersDataManager.getPreparedProposerInfo()).thenReturn(preparedProposerInfoMap);
    custodyGroupCountManager.onSlot(UInt64.ZERO); // initialize custody manager

    final List<UInt64> custodyColumnIndices = custodyGroupCountManager.getCustodyColumnIndices();
    for (UInt64 columnIndex : custodyColumnIndices) {
      when(custody.hasCustodyDataColumnSidecar(
              eq(new DataColumnSlotAndIdentifier(UInt64.ZERO, blockRoot, columnIndex))))
          .thenReturn(SafeFuture.completedFuture(true));
    }
    final List<UInt64> samplingNonCustodyColumns = new ArrayList<>();
    for (UInt64 columnIndex : custodyGroupCountManager.getSamplingColumnIndices()) {
      if (!custodyColumnIndices.contains(columnIndex)) {
        samplingNonCustodyColumns.add(columnIndex);
        when(custody.hasCustodyDataColumnSidecar(
                eq(new DataColumnSlotAndIdentifier(UInt64.ZERO, blockRoot, columnIndex))))
            .thenReturn(SafeFuture.completedFuture(false));
      }
    }

    for (final UInt64 missingColumn : samplingNonCustodyColumns) {
      LOG.info("Retrieve missing column {}", missingColumn);
      when(retriever.retrieve(
              new DataColumnSlotAndIdentifier(UInt64.ZERO, blockRoot, missingColumn)))
          .thenReturn(
              SafeFuture.completedFuture(
                  dataStructureUtil.randomDataColumnSidecar(
                      dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.ZERO),
                      missingColumn)));
      when(custody.onNewValidatedDataColumnSidecar(any())).thenReturn(SafeFuture.COMPLETE);
    }

    final SafeFuture<List<UInt64>> result = sampler.checkDataAvailability(UInt64.ZERO, blockRoot);
    final List<UInt64> availableColumns = result.join();

    // Add assertions
    assertThat(availableColumns).containsAll(custodyGroupCountManager.getCustodyColumnIndices());
    assertThat(availableColumns)
        .containsExactlyInAnyOrderElementsOf(custodyGroupCountManager.getSamplingColumnIndices());

    // Don't retrieve Datacolumn sidecars that were already in custody.
    for (UInt64 custodyColumn : custodyColumnIndices) {
      verify(retriever, never())
          .retrieve(eq(new DataColumnSlotAndIdentifier(UInt64.ZERO, blockRoot, custodyColumn)));
    }

    assertEquals(expectedSamplingRequests, samplingNonCustodyColumns.size());
  }
}
