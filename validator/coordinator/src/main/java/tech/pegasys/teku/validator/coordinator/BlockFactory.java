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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BlockBodyContentProvider;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;

public class BlockFactory {
  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final Bytes32 graffiti;
  private final Spec spec;

  public BlockFactory(
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final Bytes32 graffiti,
      final Spec spec) {
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffiti = graffiti;
    this.spec = spec;
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
      blockPreState = spec.processSlots(previousState, slotBeforeBlock);
    }

    // Collect attestations to include
    final BeaconState blockSlotState;
    if (maybeBlockSlotState.isPresent()) {
      blockSlotState = maybeBlockSlotState.get();
    } else {
      blockSlotState = spec.processSlots(blockPreState, newSlot);
    }

    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, slotBeforeBlock);

    return spec.createNewUnsignedBlock(
            newSlot,
            spec.getBeaconProposerIndex(blockSlotState, newSlot),
            blockSlotState,
            parentRoot,
            new OnDemandBlockBodyContentProvider(
                randaoReveal, blockPreState, blockSlotState, optionalGraffiti))
        .getBlock();
  }

  private class OnDemandBlockBodyContentProvider implements BlockBodyContentProvider {
    private final BLSSignature randaoReveal;
    private final BeaconState blockPreState;
    private final BeaconState blockSlotState;
    private final Optional<Bytes32> optionalGraffiti;
    private final Eth1Data eth1Vote;

    OnDemandBlockBodyContentProvider(
        final BLSSignature randaoReveal,
        final BeaconState blockPreState,
        final BeaconState blockSlotState,
        final Optional<Bytes32> optionalGraffiti) {
      this.randaoReveal = randaoReveal;
      this.blockPreState = blockPreState;
      this.blockSlotState = blockSlotState;
      this.optionalGraffiti = optionalGraffiti;
      // Eth1 vote is needed to calculate deposits so just compute it ahead of time.
      // It's currently required for all hard forks so no advantage to delay computation.
      this.eth1Vote = eth1DataCache.getEth1Vote(blockPreState);
    }

    @Override
    public BLSSignature getRandaoReveal() {
      return randaoReveal;
    }

    @Override
    public Eth1Data getEth1Data() {
      return eth1Vote;
    }

    @Override
    public Bytes32 getGraffiti() {
      return optionalGraffiti.orElse(graffiti);
    }

    @Override
    public SszList<Attestation> getAttestations() {
      return attestationPool.getAttestationsForBlock(
          blockSlotState, new AttestationForkChecker(blockSlotState));
    }

    @Override
    public SszList<ProposerSlashing> getProposerSlashings() {
      return proposerSlashingPool.getItemsForBlock(blockSlotState);
    }

    @Override
    public SszList<AttesterSlashing> getAttesterSlashings() {
      return attesterSlashingPool.getItemsForBlock(blockSlotState);
    }

    @Override
    public SszList<Deposit> getDeposits() {
      return depositProvider.getDeposits(blockPreState, eth1Vote);
    }

    @Override
    public SszList<SignedVoluntaryExit> getVoluntaryExits() {
      return voluntaryExitPool.getItemsForBlock(blockSlotState);
    }

    @Override
    public Optional<SyncAggregate> getSyncAggregate() {
      return Optional.empty();
    }
  }
}
