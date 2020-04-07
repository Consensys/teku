/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.validator.coordinator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.util.Waiter.ensureConditionRemainsMet;
import static tech.pegasys.artemis.util.config.Constants.GENESIS_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.SLOTS_PER_EPOCH;
import static tech.pegasys.artemis.util.config.Constants.TARGET_COMMITTEE_SIZE;
import static tech.pegasys.artemis.validator.coordinator.ValidatorCoordinator.getIndicesOfOurValidators;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.MockStartBeaconStateGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartDepositGenerator;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.datastructures.validator.AttesterInformation;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeAssignmentEvent;
import tech.pegasys.artemis.statetransition.events.committee.CommitteeDismissalEvent;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.bls.bls.BLSKeyPair;
import tech.pegasys.artemis.bls.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.bls.BLSSignature;

class CommitteeAssignmentManagerTest {

  private final List<BLSKeyPair> validatorKeys =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 50);
  private final List<DepositData> depositDatas =
      new MockStartDepositGenerator().createDeposits(validatorKeys);
  private final BeaconState state =
      new MockStartBeaconStateGenerator().createInitialBeaconState(UnsignedLong.ONE, depositDatas);

  private CommitteeAssignmentManager committeeAssignmentManager;
  private Map<BLSPublicKey, ValidatorInfo> validators = new HashMap<>();
  private Map<UnsignedLong, List<AttesterInformation>> committeeAssignments;

  @BeforeEach
  void setup() {
    // Own only one validator
    validators.put(validatorKeys.get(0).getPublicKey(), new ValidatorInfo(null));

    getIndicesOfOurValidators(state, validators);
    committeeAssignments = new HashMap<>();
    committeeAssignmentManager =
        spy(new CommitteeAssignmentManager(validators, committeeAssignments));
    doReturn(BLSSignature.random(42))
        .when(committeeAssignmentManager)
        .get_slot_signature(any(), any(), any());
  }

  @Test
  void nothingAlreadyRegistered_someToRegister() throws Exception {
    EventBus eventBus = mock(EventBus.class);
    committeeAssignmentManager.updateCommitteeAssignments(
        state, UnsignedLong.valueOf(GENESIS_EPOCH), eventBus);

    Waiter.waitFor(() -> verify(eventBus, times(1)).post(any(CommitteeAssignmentEvent.class)));
    ensureConditionRemainsMet(
        () -> verify(eventBus, never()).post(any(CommitteeDismissalEvent.class)));
  }

  @Test
  void someAlreadyRegistered_someToRegister() throws Exception {
    EventBus eventBus = mock(EventBus.class);

    committeeAssignmentManager.updateCommitteeAssignments(
        state, UnsignedLong.valueOf(GENESIS_EPOCH), eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(
        state, UnsignedLong.valueOf(GENESIS_EPOCH).plus(UnsignedLong.ONE), eventBus);

    Waiter.waitFor(() -> verify(eventBus, atLeastOnce()).post(any(CommitteeAssignmentEvent.class)));
    ensureConditionRemainsMet(
        () -> verify(eventBus, never()).post(any(CommitteeDismissalEvent.class)));
  }

  @Test
  void someAlreadyRegistered_someToRegister_someToDeregister() throws Exception {
    // Set TARGET_COMMITTEE_SIZE to 1 in order to make sure there are more than 1 committees per
    // slot
    // and our Validator will be assigned to a different committee at epoch 3
    int oldTargetCommitteeSize = TARGET_COMMITTEE_SIZE;
    TARGET_COMMITTEE_SIZE = 1;

    EventBus eventBus = mock(EventBus.class);

    committeeAssignmentManager.updateCommitteeAssignments(
        state, UnsignedLong.valueOf(GENESIS_EPOCH), eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(
        state, UnsignedLong.valueOf(GENESIS_EPOCH).plus(UnsignedLong.ONE), eventBus);

    ensureConditionRemainsMet(
        () -> verify(eventBus, never()).post(any(CommitteeDismissalEvent.class)));

    UnsignedLong secondEpoch = UnsignedLong.valueOf(3);
    BeaconState newState =
        state.updated(
            state -> state.setSlot(secondEpoch.times(UnsignedLong.valueOf(SLOTS_PER_EPOCH))));
    committeeAssignmentManager.updateCommitteeAssignments(newState, secondEpoch, eventBus);

    Waiter.waitFor(() -> verify(eventBus, atLeastOnce()).post(any(CommitteeAssignmentEvent.class)));
    Waiter.waitFor(() -> verify(eventBus, times(1)).post(any(CommitteeDismissalEvent.class)));

    TARGET_COMMITTEE_SIZE = oldTargetCommitteeSize;
  }

  @Test
  void noValidators_doNothing() throws Exception {
    Map<BLSPublicKey, ValidatorInfo> newValidators = new HashMap<>(validators);
    newValidators.remove(validatorKeys.get(0).getPublicKey());
    committeeAssignmentManager =
        new CommitteeAssignmentManager(newValidators, committeeAssignments);

    EventBus eventBus = mock(EventBus.class);

    committeeAssignmentManager.updateCommitteeAssignments(state, UnsignedLong.valueOf(0), eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(state, UnsignedLong.valueOf(1), eventBus);
    committeeAssignmentManager.updateCommitteeAssignments(state, UnsignedLong.valueOf(2), eventBus);

    ensureConditionRemainsMet(
        () -> verify(eventBus, never()).post(any(CommitteeAssignmentEvent.class)));
    ensureConditionRemainsMet(
        () -> verify(eventBus, never()).post(any(CommitteeDismissalEvent.class)));
  }
}
