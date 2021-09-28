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

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.BeaconStateMerge;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.helpers.MergeTransitionHelpers;
import tech.pegasys.teku.spec.logic.common.helpers.PowBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.ssz.SszList;
import tech.pegasys.teku.ssz.type.Bytes20;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;

public class BlockFactory {

  private static final Logger LOG = LogManager.getLogger();

  private final AggregatingAttestationPool attestationPool;
  private final OperationPool<AttesterSlashing> attesterSlashingPool;
  private final OperationPool<ProposerSlashing> proposerSlashingPool;
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool;
  private final SyncCommitteeContributionPool contributionPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;
  private final Bytes32 graffiti;
  private final Bytes20 feeRecipient;
  private final Spec spec;
  private final ExecutionEngineChannel executionEngineChannel;

  // we are going to store latest payloadId returned by preparePayload for a given slot
  // in this way validator doesn't need to know anything about payloadId
  private final LRUCache<UInt64, Optional<PreparePayloadReference>> slotToPayloadIdMap;

  public BlockFactory(
      final AggregatingAttestationPool attestationPool,
      final OperationPool<AttesterSlashing> attesterSlashingPool,
      final OperationPool<ProposerSlashing> proposerSlashingPool,
      final OperationPool<SignedVoluntaryExit> voluntaryExitPool,
      final SyncCommitteeContributionPool contributionPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache,
      final Bytes32 graffiti,
      final Bytes20 feeRecipient,
      final Spec spec,
      final ExecutionEngineChannel executionEngineChannel) {
    this.attestationPool = attestationPool;
    this.attesterSlashingPool = attesterSlashingPool;
    this.proposerSlashingPool = proposerSlashingPool;
    this.voluntaryExitPool = voluntaryExitPool;
    this.contributionPool = contributionPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
    this.graffiti = graffiti;
    this.feeRecipient = feeRecipient;
    this.spec = spec;
    this.executionEngineChannel = executionEngineChannel;

    this.slotToPayloadIdMap =
        LRUCache.create(
            10); // TODO check if makes sense to remember payloadId for the latest 10 slots
  }

  public void prepareExecutionPayload(final Optional<BeaconState> maybeCurrentSlotState, UInt64 targetSlot) {
    maybeCurrentSlotState.ifPresent(
        currentSlotState -> {
          Optional<PreparePayloadReference> maybeRef = prepareExecutionPayloadRef(currentSlotState, targetSlot);
          slotToPayloadIdMap.invalidateWithNewValue(targetSlot, maybeRef);
        });
  }

  private Optional<PreparePayloadReference> prepareExecutionPayloadRef(
      final BeaconState currentSlotState, UInt64 targetSlot) {
    final Optional<BeaconStateMerge> maybeCurrentMergeState = currentSlotState.toVersionMerge();

    if (maybeCurrentMergeState.isEmpty()) {
      LOG.trace("prepareExecutionPayload - not yet in Merge state!");
      return Optional.empty();
    }
    final BeaconStateMerge currentMergeState = maybeCurrentMergeState.get();

    final UInt64 slot = currentMergeState.getSlot();
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    final UInt64 timestamp = spec.computeTimeAtSlot(currentMergeState, targetSlot);
    final Bytes32 random =
        spec.atEpoch(epoch).beaconStateAccessors().getRandaoMix(currentMergeState, epoch);

    final MergeTransitionHelpers mergeTransitionHelpers =
        spec.atSlot(slot).getMergeTransitionHelpers().orElseThrow();

    final Bytes32 executionParentHash;

    if (!mergeTransitionHelpers.isMergeComplete(currentMergeState)) {
      SpecConfigMerge specConfig = spec.getSpecConfig(epoch).toVersionMerge().orElseThrow();
      UInt256 terminalTotalDifficulty = specConfig.getTerminalTotalDifficulty();

      PowBlock powHead = mergeTransitionHelpers.getPowChainHead(executionEngineChannel);
      Optional<PowBlock> terminalPowBlock =
          getPowBlockAtTotalDifficulty(terminalTotalDifficulty, powHead, mergeTransitionHelpers);

      if (terminalPowBlock.isEmpty()) {
        LOG.info(
            ColorConsolePrinter.print(
                String.format(
                    "Produce pre-merge block: pow_block.total_difficulty(%d) < transition_total_difficulty(%d), PoW blocks left ~%d",
                    powHead.totalDifficulty.toBigInteger(),
                    specConfig.getTerminalTotalDifficulty().toBigInteger(),
                    specConfig
                        .getTerminalTotalDifficulty()
                        .subtract(powHead.totalDifficulty)
                        .divide(powHead.difficulty)
                        .add(UInt256.ONE)
                        .toBigInteger()),
                Color.CYAN));
        return Optional.empty();
      }

      // terminal block
      executionParentHash = terminalPowBlock.get().blockHash;

      LOG.info(
          ColorConsolePrinter.print(
              String.format(
                  "Produce transition block: pow_block.total_difficulty(%d) >= transition_total_difficulty(%d)",
                  powHead.totalDifficulty.toBigInteger(),
                  specConfig.getTerminalTotalDifficulty().toBigInteger()),
              Color.YELLOW));
    } else {
      executionParentHash = currentMergeState.getLatest_execution_payload_header().getBlock_hash();
    }

    final ExecutionPayloadUtil executionPayloadUtil =
        spec.atSlot(slot)
            .getExecutionPayloadUtil()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "unable to retrieve ExecutionPayloadUtil from slot " + slot));

    final UInt64 payloadId =
        executionPayloadUtil.prepareExecutionPayload(
            executionEngineChannel, executionParentHash, timestamp, random, feeRecipient);

    return Optional.of(new PreparePayloadReference(payloadId, executionParentHash));
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
      blockPreState = spec.processSlots(previousState, slotBeforeBlock, executionEngineChannel);
    }

    // Collect attestations to include
    final BeaconState blockSlotState;
    if (maybeBlockSlotState.isPresent()) {
      blockSlotState = maybeBlockSlotState.get();
    } else {
      blockSlotState = spec.processSlots(blockPreState, newSlot, executionEngineChannel);
    }
    SszList<Attestation> attestations =
        attestationPool.getAttestationsForBlock(
            blockSlotState, new AttestationForkChecker(spec, blockSlotState));

    // Collect slashings to include
    final SszList<ProposerSlashing> proposerSlashings =
        proposerSlashingPool.getItemsForBlock(blockSlotState);
    final SszList<AttesterSlashing> attesterSlashings =
        attesterSlashingPool.getItemsForBlock(blockSlotState);

    // Collect exits to include
    final SszList<SignedVoluntaryExit> voluntaryExits =
        voluntaryExitPool.getItemsForBlock(blockSlotState);

    // Collect deposits
    Eth1Data eth1Data = eth1DataCache.getEth1Vote(blockPreState);
    final SszList<Deposit> deposits = depositProvider.getDeposits(blockPreState, eth1Data);

    final Bytes32 parentRoot = spec.getBlockRootAtSlot(blockSlotState, slotBeforeBlock);
    final Eth1Data eth1Vote = eth1DataCache.getEth1Vote(blockPreState);

    return spec.createNewUnsignedBlock(
            executionEngineChannel,
            newSlot,
            spec.getBeaconProposerIndex(blockSlotState, newSlot),
            blockSlotState,
            parentRoot,
            bodyBuilder ->
                bodyBuilder
                    .randaoReveal(randaoReveal)
                    .eth1Data(eth1Vote)
                    .graffiti(optionalGraffiti.orElse(graffiti))
                    .attestations(attestations)
                    .proposerSlashings(proposerSlashings)
                    .attesterSlashings(attesterSlashings)
                    .deposits(deposits)
                    .voluntaryExits(voluntaryExits)
                    .executionPayload(() -> getExecutionPayload(blockSlotState))
                    .syncAggregate(
                        () -> contributionPool.createSyncAggregateForBlock(newSlot, parentRoot)))
        .getBlock();
  }

  private ExecutionPayload getExecutionPayload(BeaconState genericState) {
    UInt64 slot = genericState.getSlot();
    final ExecutionPayloadUtil executionPayloadUtil =
        spec.atSlot(slot).getExecutionPayloadUtil().orElseThrow();

    return getExecutionPayloadRefFromSlot(slot)
        .map(
            ref ->
                validateExecutionPayload(
                    ref.getParentHash(),
                    executionPayloadUtil.getExecutionPayload(
                        executionEngineChannel, ref.getPreparePayloadId())))
        .orElseGet(ExecutionPayload::new);
  }

  private Optional<PowBlock> getPowBlockAtTotalDifficulty(
      UInt256 totalDifficulty, PowBlock head, MergeTransitionHelpers mergeTransitionHelpers) {
    Preconditions.checkArgument(
        totalDifficulty.compareTo(UInt256.ZERO) > 0, "Expecting totalDifficulty > 0");

    PowBlock block = head;
    Optional<PowBlock> parent = Optional.empty();
    while (block.totalDifficulty.compareTo(totalDifficulty) >= 0) {
      parent = Optional.of(block);
      block = mergeTransitionHelpers.getPowBlock(executionEngineChannel, block.parentHash);
    }
    return parent;
  }

  private Optional<PreparePayloadReference> getExecutionPayloadRefFromSlot(UInt64 slot) {
    return slotToPayloadIdMap
        .getCached(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to retrieve execution payloadId from slot " + slot));
  }

  private ExecutionPayload validateExecutionPayload(
      Bytes32 expectedExecutionParentHash, ExecutionPayload executionPayload) {

    if (!executionPayload.getParent_hash().equals(expectedExecutionParentHash)) {
      throw new IllegalStateException(
          "Execution Payload returned by the execution client has an unexpected parent block hash");
    }

    return executionPayload;
  }

  private static class PreparePayloadReference {
    private final UInt64 preparePayloadId;
    private final Bytes32 parentHash;

    public PreparePayloadReference(UInt64 preparePayloadId, Bytes32 parentHash) {
      this.preparePayloadId = preparePayloadId;
      this.parentHash = parentHash;
    }

    public UInt64 getPreparePayloadId() {
      return preparePayloadId;
    }

    public Bytes32 getParentHash() {
      return parentHash;
    }
  }
}
