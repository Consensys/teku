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

package tech.pegasys.teku.validator.coordinator;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_block_root_at_slot;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.BlockProposalUtil;
import tech.pegasys.teku.core.StateTransition;
import tech.pegasys.teku.core.StateTransitionException;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;

public class BlockFactory {
  private final BlockProposalUtil blockCreator;
  private final StateTransition stateTransition;
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final Bytes32 graffiti;

  public BlockFactory(
      final BlockProposalUtil blockCreator,
      final StateTransition stateTransition,
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final Bytes32 graffiti) {
    this.blockCreator = blockCreator;
    this.stateTransition = stateTransition;
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffiti = graffiti;
  }

  public BeaconBlock createUnsignedBlock(
      final BeaconState previousState,
      final Optional<BeaconState> maybeBlockSlotState,
      final UInt64 newSlot,
      final BLSSignature randaoReveal,
      final Optional<Bytes32> optionalGraffiti)
      throws EpochProcessingException, SlotProcessingException, StateTransitionException {
    checkArgument(
        maybeBlockSlotState.isEmpty() || maybeBlockSlotState.get().getSlot().equals(newSlot),
        "Block slot state for slot %s but should be for slot %s",
        maybeBlockSlotState.map(BeaconState::getSlot).orElse(null),
        newSlot);

    // Process empty slots up to the one before the new block slot
    final UInt64 slotBeforeBlock = newSlot.minus(UInt64.ONE);
    BeaconState blockPreState;
    if (previousState.getSlot().equals(slotBeforeBlock)) {
      blockPreState = previousState;
    } else {
      blockPreState = stateTransition.process_slots(previousState, slotBeforeBlock);
    }

    // Collect attestations to include
    final BeaconState blockSlotState;
    if (maybeBlockSlotState.isPresent()) {
      blockSlotState = maybeBlockSlotState.get();
    } else {
      blockSlotState = stateTransition.process_slots(blockPreState, newSlot);
    }
    SSZList<Attestation> attestations =
        attestationPool.getAttestationsForBlock(
            blockSlotState, new AttestationForkChecker(blockSlotState));

    // Collect slashings to include
    final SSZList<ProposerSlashing> proposerSlashings =
        proposerSlashingPool.getItemsForBlock(blockSlotState);
    final SSZList<AttesterSlashing> attesterSlashings =
        attesterSlashingPool.getItemsForBlock(blockSlotState);

    // Collect exits to include
    final SSZList<SignedVoluntaryExit> voluntaryExits =
        voluntaryExitPool.getItemsForBlock(blockSlotState);

    // Collect deposits
    Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockPreState);
    final SSZList<Deposit> deposits = depositProvider.getDeposits(blockPreState, eth1Data);

    final Bytes32 parentRoot = get_block_root_at_slot(blockSlotState, slotBeforeBlock);

    return blockCreator
        .createNewUnsignedBlock(
            newSlot,
            get_beacon_proposer_index(blockSlotState, newSlot),
            randaoReveal,
            blockSlotState,
            parentRoot,
            eth1Data,
            optionalGraffiti.orElse(graffiti),
            attestations,
            proposerSlashings,
            attesterSlashings,
            deposits,
            voluntaryExits)
        .getBlock();
  }
}
