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

package tech.pegasys.teku.networking.eth2.gossip.topics.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.networking.eth2.gossip.topics.validation.InternalValidationResult.REJECT;

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.operationvalidators.AttesterSlashingStateTransitionValidator;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.statetransition.BeaconChainUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidatorTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private RecentChainData recentChainData;
  private BeaconChainUtil beaconChainUtil;
  private AttesterSlashingValidator attesterSlashingValidator;
  private AttesterSlashingStateTransitionValidator stateTransitionValidator;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(new EventBus());
    beaconChainUtil = BeaconChainUtil.create(recentChainData, VALIDATOR_KEYS, true);
    stateTransitionValidator = mock(AttesterSlashingStateTransitionValidator.class);
    attesterSlashingValidator =
        new AttesterSlashingValidator(recentChainData, stateTransitionValidator);
  }

  @Test
  public void shouldAcceptValidAttesterSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(stateTransitionValidator.validateSlashing(
            eq(recentChainData.getBestState().orElseThrow()), eq(slashing), any()))
        .thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validate(slashing)).isEqualTo(ACCEPT);
  }

  @Test
  public void shouldRejectInvalidAttesterSlashing() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    AttesterSlashing slashing = dataStructureUtil.randomAttesterSlashing();
    when(stateTransitionValidator.validateSlashing(
            eq(recentChainData.getBestState().orElseThrow()), eq(slashing), any()))
        .thenReturn(
            Optional.of(
                AttesterSlashingStateTransitionValidator.AttesterSlashingInvalidReason
                    .ATTESTATIONS_NOT_SLASHABLE));
    assertThat(attesterSlashingValidator.validate(slashing)).isEqualTo(REJECT);
  }

  @Test
  public void shouldIgnoreAttesterSlashingForTheSameAttesters() throws Exception {
    beaconChainUtil.initializeStorage();
    beaconChainUtil.createAndImportBlockAtSlot(6);
    AttesterSlashing slashing1 = dataStructureUtil.randomAttesterSlashing();
    AttesterSlashing slashing2 =
        new AttesterSlashing(slashing1.getAttestation_1(), slashing1.getAttestation_2());
    when(stateTransitionValidator.validateSlashing(
            eq(recentChainData.getBestState().orElseThrow()), any()))
        .thenReturn(Optional.empty());
    assertThat(attesterSlashingValidator.validate(slashing1)).isEqualTo(ACCEPT);
    assertThat(attesterSlashingValidator.validate(slashing2)).isEqualTo(IGNORE);
  }
}
