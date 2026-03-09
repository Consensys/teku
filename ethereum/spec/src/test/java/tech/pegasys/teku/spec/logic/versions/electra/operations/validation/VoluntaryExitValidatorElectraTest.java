/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.electra.operations.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

class VoluntaryExitValidatorElectraTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);

  private RecentChainData recentChainData;
  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private VoluntaryExitValidatorElectra validatorElectra;

  private final PredicatesElectra predicates = new PredicatesElectra(spec.getGenesisSpecConfig());
  private final BeaconStateAccessorsElectra stateAccessors =
      mock(BeaconStateAccessorsElectra.class);

  @BeforeEach
  void setup() throws Exception {
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final BeaconChainUtil beaconChainUtil =
        BeaconChainUtil.create(spec, recentChainData, VALIDATOR_KEYS, true);
    validatorElectra =
        new VoluntaryExitValidatorElectra(spec.getGenesisSpecConfig(), predicates, stateAccessors);

    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
  }

  @Test
  public void shouldAcceptValidVoluntaryExit() throws Exception {
    when(stateAccessors.getPendingBalanceToWithdraw(any(), anyInt())).thenReturn(UInt64.ZERO);

    final SignedVoluntaryExit exit =
        dataStructureUtil.randomSignedVoluntaryExit(dataStructureUtil.randomUInt64(24));
    final BeaconStateElectra stateElectra =
        BeaconStateElectra.required(recentChainData.getBestState().orElseThrow().get());
    assertThat(validatorElectra.validateElectraConditions(stateElectra, exit)).isEmpty();
  }

  @Test
  public void shouldRejectValidVoluntaryExitWithPendingWithdrawals() throws Exception {
    when(stateAccessors.getPendingBalanceToWithdraw(any(), anyInt())).thenReturn(UInt64.ONE);

    final SignedVoluntaryExit exit =
        dataStructureUtil.randomSignedVoluntaryExit(dataStructureUtil.randomUInt64(24));
    final BeaconStateElectra stateElectra =
        BeaconStateElectra.required(recentChainData.getBestState().orElseThrow().get());
    assertThat(validatorElectra.validateElectraConditions(stateElectra, exit))
        .contains(VoluntaryExitValidator.ExitInvalidReason.pendingWithdrawalsInQueue());
  }
}
