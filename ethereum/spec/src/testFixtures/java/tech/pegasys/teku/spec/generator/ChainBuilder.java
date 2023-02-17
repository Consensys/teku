/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecarSchema;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.datastructures.util.BlobsUtil;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.signatures.Signer;

/** A utility for building small, valid chains of blocks with states for testing */
public class ChainBuilder {
  private static final List<BLSKeyPair> DEFAULT_VALIDATOR_KEYS =
      Collections.unmodifiableList(new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 3));
  private static final int RANDOM_BLOBS_COUNT = 2;
  private final Spec spec;
  private final List<BLSKeyPair> validatorKeys;
  private final AttestationGenerator attestationGenerator;
  private final AttesterSlashingGenerator attesterSlashingGenerator;
  private final NavigableMap<UInt64, SignedBlockAndState> blocks = new TreeMap<>();
  private final NavigableMap<UInt64, BlobsSidecar> blobsSidecars = new TreeMap<>();
  private final NavigableMap<UInt64, BlobSidecar> blobSidecars = new TreeMap<>();
  private final Map<Bytes32, SignedBlockAndState> blocksByHash = new HashMap<>();
  private final Map<Bytes32, BlobsSidecar> blobsSidecarsByHash = new HashMap<>();
  private final Map<Bytes32, BlobSidecar> blobSidecarsByHash = new HashMap<>();
  private final BlockProposalTestUtil blockProposalTestUtil;
  private final BlobsUtil blobsUtil;

  private ChainBuilder(
      final Spec spec,
      final List<BLSKeyPair> validatorKeys,
      final Map<UInt64, SignedBlockAndState> existingBlocks,
      final Map<UInt64, BlobsSidecar> existingBlobsSidecars,
      final Map<UInt64, BlobSidecar> existingBlobSidecars) {
    this.spec = spec;
    this.validatorKeys = validatorKeys;
    this.blobsUtil = new BlobsUtil(spec);
    attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    attesterSlashingGenerator = new AttesterSlashingGenerator(spec, validatorKeys);
    blockProposalTestUtil = new BlockProposalTestUtil(spec);
    blocks.putAll(existingBlocks);
    blobsSidecars.putAll(existingBlobsSidecars);
    blobSidecars.putAll(existingBlobSidecars);
    existingBlocks.values().forEach(b -> blocksByHash.put(b.getRoot(), b));
    blobsSidecars.values().forEach(b -> blobsSidecarsByHash.put(b.getBeaconBlockRoot(), b));
    blobSidecars.values().forEach(b -> blobSidecarsByHash.put(b.getBlockRoot(), b));
  }

  public static ChainBuilder create(final Spec spec) {
    return ChainBuilder.create(spec, DEFAULT_VALIDATOR_KEYS);
  }

  public static ChainBuilder create(final Spec spec, final List<BLSKeyPair> validatorKeys) {
    return new ChainBuilder(
        spec,
        validatorKeys,
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  public Optional<SignedBeaconBlock> getBlock(final Bytes32 blockRoot) {
    return Optional.ofNullable(blocksByHash.get(blockRoot)).map(SignedBlockAndState::getBlock);
  }

  public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    return Optional.ofNullable(blocksByHash.get(blockRoot));
  }

  public Optional<BlobsSidecar> getBlobsSidecar(final Bytes32 blockRoot) {
    return Optional.ofNullable(blobsSidecarsByHash.get(blockRoot));
  }

  public Optional<BlobSidecar> getBlobSidecar(final Bytes32 blockRoot) {
    return Optional.ofNullable(blobSidecarsByHash.get(blockRoot));
  }

  public Optional<SignedBeaconBlockAndBlobsSidecar> getBlockAndBlobsSidecar(
      final Bytes32 blockRoot) {
    return getBlock(blockRoot)
        .map(
            block -> {
              final UInt64 slot = block.getSlot();
              final SchemaDefinitionsDeneb schemaDefinitionsDeneb =
                  SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions());
              final BlobsSidecar blobsSidecar =
                  getBlobsSidecar(blockRoot)
                      .orElse(
                          schemaDefinitionsDeneb
                              .getBlobsSidecarSchema()
                              .createEmpty(blockRoot, slot));
              return schemaDefinitionsDeneb
                  .getSignedBeaconBlockAndBlobsSidecarSchema()
                  .create(block, blobsSidecar);
            });
  }

  /**
   * Create an independent {@code ChainBuilder} with the same history as the current builder. This
   * independent copy can now create a divergent chain.
   *
   * @return An independent copy of this ChainBuilder
   */
  public ChainBuilder fork() {
    return new ChainBuilder(spec, validatorKeys, blocks, blobsSidecars, blobSidecars);
  }

  public List<BLSKeyPair> getValidatorKeys() {
    return validatorKeys;
  }

  public UInt64 getLatestSlot() {
    assertChainIsNotEmpty();
    return getLatestBlockAndState().getBlock().getSlot();
  }

  public UInt64 getLatestEpoch() {
    assertChainIsNotEmpty();
    final UInt64 slot = getLatestSlot();
    return spec.computeEpochAtSlot(slot);
  }

  public Stream<SignedBlockAndState> streamBlocksAndStates() {
    return blocks.values().stream();
  }

  public Stream<SignedBlockAndState> streamBlocksAndStates(final long fromSlot, final long toSlot) {
    return streamBlocksAndStates(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot));
  }

  public Stream<SignedBlockAndState> streamBlocksAndStates(final long fromSlot) {
    return streamBlocksAndStates(UInt64.valueOf(fromSlot));
  }

  public Stream<SignedBlockAndState> streamBlocksAndStates(final UInt64 fromSlot) {
    return streamBlocksAndStates(fromSlot, getLatestSlot());
  }

  public Stream<SignedBlockAndState> streamBlocksAndStates(
      final UInt64 fromSlot, final UInt64 toSlot) {
    return blocks.values().stream()
        .filter(b -> b.getBlock().getSlot().isGreaterThanOrEqualTo(fromSlot))
        .filter(b -> b.getBlock().getSlot().isLessThanOrEqualTo(toSlot));
  }

  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final long toSlot) {
    return streamBlocksAndStatesUpTo(UInt64.valueOf(toSlot));
  }

  public Stream<SignedBlockAndState> streamBlocksAndStatesUpTo(final UInt64 toSlot) {
    return blocks.values().stream().filter(b -> b.getBlock().getSlot().compareTo(toSlot) <= 0);
  }

  public Stream<BlobsSidecar> streamBlobsSidecars(final long fromSlot, final long toSlot) {
    return streamBlobsSidecars(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot));
  }

  public Stream<BlobSidecar> streamBlobSidecars(final long fromSlot, final long toSlot) {
    return streamBlobSidecars(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot));
  }

  public Stream<BlobsSidecar> streamBlobsSidecars(final UInt64 fromSlot, final UInt64 toSlot) {
    return blobsSidecars.values().stream()
        .filter(s -> s.getBeaconBlockSlot().isGreaterThanOrEqualTo(fromSlot))
        .filter(s -> s.getBeaconBlockSlot().isLessThanOrEqualTo(toSlot));
  }

  public Stream<BlobSidecar> streamBlobSidecars(final UInt64 fromSlot, final UInt64 toSlot) {
    return blobSidecars.values().stream()
        .filter(s -> s.getSlot().isGreaterThanOrEqualTo(fromSlot))
        .filter(s -> s.getSlot().isLessThanOrEqualTo(toSlot));
  }

  public Stream<BlobsSidecar> streamBlobsSidecars() {
    return blobsSidecars.values().stream();
  }

  public void discardBlobsSidecar(final UInt64 slot) {
    blobsSidecars.remove(slot);
  }

  public SignedBlockAndState getGenesis() {
    return Optional.ofNullable(blocks.firstEntry()).map(Map.Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getLatestBlockAndState() {
    return Optional.ofNullable(blocks.lastEntry()).map(Map.Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getBlockAndStateAtSlot(final long slot) {
    return getBlockAndStateAtSlot(UInt64.valueOf(slot));
  }

  public SignedBlockAndState getBlockAndStateAtSlot(final UInt64 slot) {
    return blocks.get(slot);
  }

  public SignedBeaconBlock getBlockAtSlot(final long slot) {
    return getBlockAtSlot(UInt64.valueOf(slot));
  }

  public SignedBeaconBlock getBlockAtSlot(final UInt64 slot) {
    return resultToBlock(getBlockAndStateAtSlot(slot));
  }

  public BeaconState getStateAtSlot(final long slot) {
    return getStateAtSlot(UInt64.valueOf(slot));
  }

  public BeaconState getStateAtSlot(final UInt64 slot) {
    return resultToState(getBlockAndStateAtSlot(slot));
  }

  public SignedBlockAndState getLatestBlockAndStateAtSlot(final long slot) {
    return getLatestBlockAndStateAtSlot(UInt64.valueOf(slot));
  }

  public SignedBlockAndState getLatestBlockAndStateAtSlot(final UInt64 slot) {
    return Optional.ofNullable(blocks.floorEntry(slot)).map(Map.Entry::getValue).orElse(null);
  }

  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final long epoch) {
    return getLatestBlockAndStateAtEpochBoundary(UInt64.valueOf(epoch));
  }

  public SignedBlockAndState getLatestBlockAndStateAtEpochBoundary(final UInt64 epoch) {
    assertChainIsNotEmpty();
    final UInt64 slot = spec.computeStartSlotAtEpoch(epoch);
    return getLatestBlockAndStateAtSlot(slot);
  }

  public Checkpoint getCurrentCheckpointForEpoch(final long epoch) {
    return getCurrentCheckpointForEpoch(UInt64.valueOf(epoch));
  }

  public Checkpoint getCurrentCheckpointForEpoch(final UInt64 epoch) {
    assertChainIsNotEmpty();
    final SignedBeaconBlock block = getLatestBlockAndStateAtEpochBoundary(epoch).getBlock();
    return new Checkpoint(epoch, block.getMessage().hashTreeRoot());
  }

  public void initializeGenesis(final BeaconState genesis) {
    addGenesisBlock(genesis);
  }

  public SignedBlockAndState generateGenesis() {
    return generateGenesis(UInt64.ZERO, true);
  }

  public SignedBlockAndState generateGenesis(final UInt64 genesisTime, final boolean signDeposits) {
    return generateGenesis(genesisTime, signDeposits, Optional.empty());
  }

  public SignedBlockAndState generateGenesis(
      final UInt64 genesisTime,
      final boolean signDeposits,
      final Optional<ExecutionPayloadHeader> payloadHeader) {
    checkState(blocks.isEmpty(), "Genesis already created");

    // Generate genesis state
    BeaconState genesisState =
        new GenesisStateBuilder()
            .spec(spec)
            .signDeposits(signDeposits)
            .addValidators(validatorKeys)
            .genesisTime(genesisTime)
            .executionPayloadHeader(payloadHeader)
            .build();

    return addGenesisBlock(genesisState);
  }

  private SignedBlockAndState addGenesisBlock(final BeaconState genesisState) {
    // Generate genesis block
    BeaconBlock genesisBlock = BeaconBlock.fromGenesisState(spec, genesisState);
    final SignedBeaconBlock signedBlock =
        SignedBeaconBlock.create(spec, genesisBlock, BLSSignature.empty());

    final SignedBlockAndState blockAndState = new SignedBlockAndState(signedBlock, genesisState);
    trackBlock(blockAndState);

    // add an empty blobs sidecar to the genesis block if genesis is in the Deneb milestone
    spec.getGenesisSchemaDefinitions()
        .toVersionDeneb()
        .ifPresent(
            schemaDefinitions -> {
              final BlobsSidecarSchema blobsSidecarSchema =
                  schemaDefinitions.getBlobsSidecarSchema();
              final BlobsSidecar blobsSidecar =
                  blobsSidecarSchema.createEmpty(blockAndState.getRoot(), blockAndState.getSlot());
              trackBlobsSidecar(blobsSidecar);
            });

    return blockAndState;
  }

  public List<SignedBlockAndState> generateBlocksUpToSlot(final long slot) {
    return generateBlocksUpToSlot(UInt64.valueOf(slot));
  }

  public List<SignedBlockAndState> generateBlocksUpToSlot(final UInt64 slot) {
    assertBlockCanBeGenerated();
    final List<SignedBlockAndState> generated = new ArrayList<>();

    SignedBlockAndState latestBlock = getLatestBlockAndState();
    while (latestBlock.getState().getSlot().compareTo(slot) < 0) {
      latestBlock = generateNextBlock();
      generated.add(latestBlock);
    }

    return generated;
  }

  public SignedBlockAndState generateNextBlock() {
    assertBlockCanBeGenerated();
    return generateNextBlock(0);
  }

  public SignedBlockAndState generateNextBlock(final int skipSlots) {
    assertBlockCanBeGenerated();
    final SignedBlockAndState latest = getLatestBlockAndState();
    final UInt64 nextSlot = latest.getState().getSlot().plus(1 + skipSlots);
    return generateBlockAtSlot(nextSlot);
  }

  public SignedBlockAndState generateBlockAtSlot(final long slot) {
    return generateBlockAtSlot(UInt64.valueOf(slot));
  }

  public SignedBlockAndState generateBlockAtSlot(final UInt64 slot) {
    return generateBlockAtSlot(slot, BlockOptions.create());
  }

  public SignedBlockAndState generateBlockAtSlot(final long slot, final BlockOptions options) {
    return generateBlockAtSlot(UInt64.valueOf(slot), options);
  }

  public SignedBlockAndState generateBlockAtSlot(final UInt64 slot, final BlockOptions options) {
    assertBlockCanBeGenerated();
    final SignedBlockAndState latest = getLatestBlockAndState();
    checkState(
        slot.compareTo(latest.getState().getSlot()) > 0,
        "Cannot generate block at historical slot. Latest slot "
            + latest.getState().getSlot()
            + " asked for: "
            + slot);

    return appendNewBlockToChain(slot, options);
  }

  public List<SignedBlockAndState> finalizeCurrentChain() {
    final UInt64 chainHeadSlot = getLatestSlot();
    final UInt64 finalizeEpoch = spec.computeEpochAtSlot(chainHeadSlot).max(2);
    final UInt64 finalHeadEpoch = finalizeEpoch.plus(3);
    final UInt64 finalHeadSlot = spec.computeStartSlotAtEpoch(finalHeadEpoch);

    final List<SignedBlockAndState> addedBlockAndStates = new ArrayList<>();
    SignedBlockAndState newChainHead = null;
    for (UInt64 slot = chainHeadSlot.plus(1);
        slot.isLessThan(finalHeadSlot);
        slot = slot.increment()) {
      final BlockOptions blockOptions = BlockOptions.create();
      streamValidAttestationsForBlockAtSlot(slot).forEach(blockOptions::addAttestation);
      newChainHead = generateBlockAtSlot(slot, blockOptions);
      addedBlockAndStates.add(newChainHead);
    }
    final Checkpoint finalizedCheckpoint = newChainHead.getState().getFinalizedCheckpoint();
    assertThat(finalizedCheckpoint.getEpoch())
        .describedAs("Failed to finalize epoch %s", finalizeEpoch)
        .isEqualTo(finalizeEpoch);
    assertThat(finalizedCheckpoint.getRoot())
        .describedAs("Failed to finalize epoch %s", finalizeEpoch)
        .isNotEqualTo(Bytes32.ZERO);
    return addedBlockAndStates;
  }

  /**
   * Utility for streaming valid attestations available for inclusion at the given slot. This
   * utility can be used to assign valid attestations to a generated block.
   *
   * @param slot The slot at which attestations are to be included
   * @return A stream of valid attestations that can be included in a block generated at the given
   *     slot
   */
  public Stream<Attestation> streamValidAttestationsForBlockAtSlot(final long slot) {
    return streamValidAttestationsForBlockAtSlot(UInt64.valueOf(slot));
  }

  /**
   * Utility for streaming valid attestations available for inclusion at the given slot. This
   * utility can be used to assign valid attestations to a generated block.
   *
   * @param slot The slot at which attestations are to be included
   * @return A stream of valid attestations that can be included in a block generated at the given
   *     slot
   */
  public Stream<Attestation> streamValidAttestationsForBlockAtSlot(final UInt64 slot) {
    // Calculate bounds for valid head blocks
    final UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    final UInt64 prevEpoch =
        currentEpoch.compareTo(UInt64.ZERO) == 0 ? currentEpoch : currentEpoch.minus(UInt64.ONE);
    final UInt64 minBlockSlot = spec.computeStartSlotAtEpoch(prevEpoch);

    // Calculate valid assigned slots to be included in a block at the given slot
    final UInt64 slotsPerEpoch = UInt64.valueOf(spec.getGenesisSpecConfig().getSlotsPerEpoch());
    final UInt64 minAssignedSlot =
        slot.compareTo(slotsPerEpoch) <= 0 ? UInt64.ZERO : slot.minus(slotsPerEpoch);
    final int minInclusionDiff = spec.getSpecConfig(currentEpoch).getMinAttestationInclusionDelay();
    final UInt64 maxAssignedSlot = slot.minusMinZero(minInclusionDiff);

    // Generate stream of consistent, valid attestations for inclusion
    return LongStream.rangeClosed(minAssignedSlot.longValue(), maxAssignedSlot.longValue())
        .mapToObj(UInt64::valueOf)
        .map(this::getLatestBlockAndStateAtSlot)
        .filter(Objects::nonNull)
        .filter(b -> b.getSlot().compareTo(minBlockSlot) >= 0)
        .flatMap(this::streamValidAttestationsWithTargetBlock);
  }

  /**
   * Utility for streaming valid attestations with a specific target block.
   *
   * @param attestedHead the block to use as the attestation target
   * @return a stream of valid attestations voting for the specified block
   */
  public Stream<Attestation> streamValidAttestationsWithTargetBlock(
      final StateAndBlockSummary attestedHead) {
    return attestationGenerator.streamAttestations(attestedHead, attestedHead.getSlot());
  }

  public AttesterSlashing createAttesterSlashingForAttestation(
      final Attestation attestation, final SignedBlockAndState blockAndState) {
    return attesterSlashingGenerator.createAttesterSlashingForAttestation(
        attestation, blockAndState);
  }

  private void assertChainIsNotEmpty() {
    checkState(!blocks.isEmpty(), "Unable to execute operation on empty chain");
  }

  private void assertBlockCanBeGenerated() {
    checkState(!blocks.isEmpty(), "Genesis block must be created before blocks can be added.");
  }

  private boolean denebMilestoneReached(final UInt64 slot) {
    return spec.getForkSchedule()
        .getSpecMilestoneAtSlot(slot)
        .isGreaterThanOrEqualTo(SpecMilestone.DENEB);
  }

  private void trackBlock(final SignedBlockAndState block) {
    blocks.put(block.getSlot(), block);
    blocksByHash.put(block.getRoot(), block);
  }

  private void trackBlobsSidecar(final BlobsSidecar blobsSidecar) {
    blobsSidecars.put(blobsSidecar.getBeaconBlockSlot(), blobsSidecar);
    blobsSidecarsByHash.put(blobsSidecar.getBeaconBlockRoot(), blobsSidecar);
  }

  private SignedBlockAndState appendNewBlockToChain(final UInt64 slot, final BlockOptions options) {
    final SignedBlockAndState latestBlockAndState = getLatestBlockAndState();
    final BeaconState preState = latestBlockAndState.getState();
    final Bytes32 parentRoot = latestBlockAndState.getBlock().getMessage().hashTreeRoot();

    int proposerIndex = blockProposalTestUtil.getProposerIndexForSlot(preState, slot);
    if (options.getWrongProposer()) {
      proposerIndex = (proposerIndex == 0 ? 1 : proposerIndex - 1);
    }
    final Signer signer = getSigner(proposerIndex);
    final SignedBlockAndState nextBlockAndState;
    try {
      SszList<Attestation> attestations =
          BeaconBlockBodyLists.ofSpec(spec)
              .createAttestations(options.getAttestations().toArray(new Attestation[0]));
      SszList<AttesterSlashing> attesterSlashings =
          BeaconBlockBodyLists.ofSpec(spec)
              .createAttesterSlashings(
                  options.getAttesterSlashings().toArray(new AttesterSlashing[0]));

      if (denebMilestoneReached(slot) && options.getGenerateRandomBlobs()) {
        nextBlockAndState =
            generateBlockWithRandomBlobsSidecar(
                slot, options, preState, parentRoot, signer, attestations, attesterSlashings);
      } else if (denebMilestoneReached(slot)) {
        nextBlockAndState =
            generateBlockWithBlobsSidecar(
                slot, options, preState, parentRoot, signer, attestations, attesterSlashings);
      } else {
        nextBlockAndState =
            generateBlock(
                slot, options, preState, parentRoot, signer, attestations, attesterSlashings);
      }
      trackBlock(nextBlockAndState);
      return nextBlockAndState;
    } catch (EpochProcessingException | SlotProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private SignedBlockAndState generateBlock(
      final UInt64 slot,
      final BlockOptions options,
      final BeaconState preState,
      final Bytes32 parentRoot,
      final Signer signer,
      final SszList<Attestation> attestations,
      final SszList<AttesterSlashing> attesterSlashings)
      throws EpochProcessingException, SlotProcessingException {
    return SafeFutureAssert.safeJoin(
        blockProposalTestUtil.createBlock(
            signer,
            slot,
            preState,
            parentRoot,
            Optional.of(attestations),
            Optional.empty(),
            Optional.of(attesterSlashings),
            Optional.empty(),
            options.getEth1Data(),
            options.getTransactions(),
            options.getTerminalBlockHash(),
            options.getExecutionPayload(),
            options.getBlsToExecutionChange(),
            options.getKzgCommitments(),
            options.getSkipStateTransition()));
  }

  private SignedBlockAndState generateBlockWithBlobsSidecar(
      final UInt64 slot,
      final BlockOptions options,
      final BeaconState preState,
      final Bytes32 parentRoot,
      final Signer signer,
      final SszList<Attestation> attestations,
      final SszList<AttesterSlashing> attesterSlashings)
      throws EpochProcessingException, SlotProcessingException {
    final SignedBlockAndState nextBlockAndState =
        SafeFutureAssert.safeJoin(
            blockProposalTestUtil.createBlock(
                signer,
                slot,
                preState,
                parentRoot,
                Optional.of(attestations),
                Optional.empty(),
                Optional.of(attesterSlashings),
                Optional.empty(),
                options.getEth1Data(),
                options.getTransactions(),
                options.getTerminalBlockHash(),
                options.getExecutionPayload(),
                options.getBlsToExecutionChange(),
                options.getKzgCommitments(),
                options.getSkipStateTransition()));

    final BlobsSidecarSchema blobsSidecarSchema =
        SchemaDefinitionsDeneb.required(spec.atSlot(slot).getSchemaDefinitions())
            .getBlobsSidecarSchema();

    if (options.isStoreBlobsSidecarEnabled()) {
      final BlobsSidecar blobsSidecar =
          options.blobsSidecar.orElseGet(
              () ->
                  new BlobsSidecar(
                      blobsSidecarSchema,
                      nextBlockAndState.getRoot(),
                      slot,
                      options.getBlobs().orElse(List.of()),
                      options.getKzgProof().orElse(KZGProof.INFINITY)));
      trackBlobsSidecar(blobsSidecar);
    }

    return nextBlockAndState;
  }

  private SignedBlockAndState generateBlockWithRandomBlobsSidecar(
      final UInt64 slot,
      final BlockOptions options,
      final BeaconState preState,
      final Bytes32 parentRoot,
      final Signer signer,
      final SszList<Attestation> attestations,
      final SszList<AttesterSlashing> attesterSlashings)
      throws EpochProcessingException, SlotProcessingException {
    List<Blob> randomBlobs = blobsUtil.generateBlobs(slot, RANDOM_BLOBS_COUNT);

    final SignedBlockAndState nextBlockAndState =
        SafeFutureAssert.safeJoin(
            blockProposalTestUtil.createBlockWithBlobs(
                signer,
                slot,
                preState,
                parentRoot,
                Optional.of(attestations),
                Optional.empty(),
                Optional.of(attesterSlashings),
                Optional.empty(),
                options.getEth1Data(),
                options.getTransactions(),
                options.getTerminalBlockHash(),
                options.getExecutionPayload(),
                options.getBlsToExecutionChange(),
                randomBlobs,
                options.getSkipStateTransition()));

    final BlobsSidecarSchema blobsSidecarSchema =
        spec.getGenesisSchemaDefinitions().toVersionDeneb().orElseThrow().getBlobsSidecarSchema();

    final MiscHelpersDeneb miscHelpers =
        spec.forMilestone(SpecMilestone.DENEB).miscHelpers().toVersionDeneb().orElseThrow();
    KZGProof kzgProof =
        miscHelpers.computeAggregatedKzgProof(
            randomBlobs.stream().map(Blob::getBytes).collect(Collectors.toList()));

    if (options.isStoreBlobsSidecarEnabled()) {
      final BlobsSidecar blobsSidecar =
          new BlobsSidecar(
              blobsSidecarSchema, nextBlockAndState.getRoot(), slot, randomBlobs, kzgProof);

      trackBlobsSidecar(blobsSidecar);
    }
    return nextBlockAndState;
  }

  private BeaconState resultToState(final SignedBlockAndState result) {
    return Optional.ofNullable(result).map(SignedBlockAndState::getState).orElse(null);
  }

  private SignedBeaconBlock resultToBlock(final SignedBlockAndState result) {
    return Optional.ofNullable(result).map(SignedBlockAndState::getBlock).orElse(null);
  }

  public SignedContributionAndProofTestBuilder createValidSignedContributionAndProofBuilder() {
    return createValidSignedContributionAndProofBuilder(getLatestSlot());
  }

  public SignedContributionAndProofTestBuilder createValidSignedContributionAndProofBuilder(
      final UInt64 slot) {
    return createValidSignedContributionAndProofBuilder(slot, getLatestBlockAndState().getRoot());
  }

  public SignedContributionAndProofTestBuilder createValidSignedContributionAndProofBuilder(
      final UInt64 slot, final Bytes32 beaconBlockRoot) {
    return createValidSignedContributionAndProofBuilder(slot, beaconBlockRoot, Optional.empty());
  }

  public SignedContributionAndProofTestBuilder createValidSignedContributionAndProofBuilder(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final Optional<Integer> requiredSubcommittee) {
    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(slot);
    final SignedBlockAndState latestBlockAndState = getLatestBlockAndState();
    final UInt64 epoch = syncCommitteeUtil.getEpochForDutiesAtSlot(slot);

    final Map<UInt64, SyncSubcommitteeAssignments> subcommitteeAssignments =
        syncCommitteeUtil.getSyncSubcommittees(latestBlockAndState.getState(), epoch);
    for (Map.Entry<UInt64, SyncSubcommitteeAssignments> entry :
        subcommitteeAssignments.entrySet()) {
      final UInt64 validatorIndex = entry.getKey();
      final Signer signer = getSigner(validatorIndex.intValue());
      final SyncSubcommitteeAssignments assignments = entry.getValue();
      for (int subcommitteeIndex : assignments.getAssignedSubcommittees()) {
        if (requiredSubcommittee.isPresent() && requiredSubcommittee.get() != subcommitteeIndex) {
          continue;
        }
        final SyncAggregatorSelectionData syncAggregatorSelectionData =
            syncCommitteeUtil.createSyncAggregatorSelectionData(
                slot, UInt64.valueOf(subcommitteeIndex));
        final ForkInfo forkInfo =
            new ForkInfo(
                spec.fork(epoch), latestBlockAndState.getState().getGenesisValidatorsRoot());
        final BLSSignature proof =
            signer.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo).join();
        if (syncCommitteeUtil.isSyncCommitteeAggregator(proof)) {
          return new SignedContributionAndProofTestBuilder()
              .signerProvider(this::getSigner)
              .syncCommitteeUtil(syncCommitteeUtil)
              .spec(spec)
              .state(latestBlockAndState.getState())
              .subcommitteeIndex(subcommitteeIndex)
              .slot(slot)
              .selectionProof(proof)
              .beaconBlockRoot(beaconBlockRoot)
              .aggregator(validatorIndex, signer);
        }
      }
    }
    throw new IllegalStateException("No valid sync subcommittee aggregators found");
  }

  public SyncCommitteeMessage createValidSyncCommitteeMessage() {
    final SignedBlockAndState target = getLatestBlockAndState();
    return createSyncCommitteeMessage(target.getSlot(), target.getRoot());
  }

  public SyncCommitteeMessage createSyncCommitteeMessage(
      final UInt64 slot, final Bytes32 blockRoot) {
    final BeaconStateAltair state =
        BeaconStateAltair.required(getLatestBlockAndStateAtSlot(slot).getState());

    final BLSPublicKey pubKey =
        state.getCurrentSyncCommittee().getPubkeys().get(0).getBLSPublicKey();
    return createSyncCommitteeMessage(slot, blockRoot, state, pubKey);
  }

  public SyncCommitteeMessage createSyncCommitteeMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final BeaconStateAltair state,
      final BLSPublicKey validatorPublicKey) {
    final int validatorIndex = spec.getValidatorIndex(state, validatorPublicKey).orElseThrow();

    final UInt64 epoch = spec.getSyncCommitteeUtilRequired(slot).getEpochForDutiesAtSlot(slot);
    final ForkInfo forkInfo = new ForkInfo(spec.fork(epoch), state.getGenesisValidatorsRoot());
    final BLSSignature signature =
        getSigner(validatorIndex).signSyncCommitteeMessage(slot, blockRoot, forkInfo).join();
    return SchemaDefinitionsAltair.required(spec.atSlot(slot).getSchemaDefinitions())
        .getSyncCommitteeMessageSchema()
        .create(slot, blockRoot, UInt64.valueOf(validatorIndex), signature);
  }

  public BLSSignature sign(
      final int validatorId, final Function<Signer, SafeFuture<BLSSignature>> signFunction) {
    final SafeFuture<BLSSignature> result = signFunction.apply(getSigner(validatorId));
    assertThat(result).isCompleted();
    return result.join();
  }

  public Signer getSigner(final int validatorId) {
    return new LocalSigner(spec, validatorKeys.get(validatorId), SyncAsyncRunner.SYNC_RUNNER);
  }

  public static final class BlockOptions {

    private final List<Attestation> attestations = new ArrayList<>();
    private final List<AttesterSlashing> attesterSlashings = new ArrayList<>();
    private Optional<Eth1Data> eth1Data = Optional.empty();
    private Optional<List<Bytes>> transactions = Optional.empty();
    private Optional<Bytes32> terminalBlockHash = Optional.empty();
    private Optional<ExecutionPayload> executionPayload = Optional.empty();
    private Optional<SszList<SignedBlsToExecutionChange>> blsToExecutionChange = Optional.empty();
    private Optional<SszList<SszKZGCommitment>> kzgCommitments = Optional.empty();
    private Optional<List<Blob>> blobs = Optional.empty();
    private Optional<KZGProof> kzgProof = Optional.empty();
    private Optional<BlobsSidecar> blobsSidecar = Optional.empty();
    private boolean generateRandomBlobs = false;
    private boolean storeBlobsSidecar = true;
    private boolean skipStateTransition = false;
    private boolean wrongProposer = false;

    private BlockOptions() {}

    public static BlockOptions create() {
      return new BlockOptions();
    }

    public BlockOptions addAttestation(final Attestation attestation) {
      attestations.add(attestation);
      return this;
    }

    public BlockOptions addAttesterSlashing(final AttesterSlashing attesterSlashing) {
      attesterSlashings.add(attesterSlashing);
      return this;
    }

    public BlockOptions setEth1Data(final Eth1Data eth1Data) {
      this.eth1Data = Optional.ofNullable(eth1Data);
      return this;
    }

    public BlockOptions setTransactions(final Bytes... transactions) {
      this.transactions = Optional.of(List.of(transactions));
      return this;
    }

    public BlockOptions setTerminalBlockHash(final Bytes32 blockHash) {
      this.terminalBlockHash = Optional.of(blockHash);
      return this;
    }

    public BlockOptions setBlsToExecutionChange(
        final SszList<SignedBlsToExecutionChange> blsToExecutionChange) {
      this.blsToExecutionChange = Optional.of(blsToExecutionChange);
      return this;
    }

    public BlockOptions setBlobs(final List<Blob> blobs) {
      this.blobs = Optional.of(blobs);
      return this;
    }

    public BlockOptions setKzgProof(final KZGProof kzgProof) {
      this.kzgProof = Optional.of(kzgProof);
      return this;
    }

    public BlockOptions setBlobsSidecar(final BlobsSidecar blobsSidecar) {
      this.blobsSidecar = Optional.of(blobsSidecar);
      return this;
    }

    public BlockOptions setKzgCommitments(final SszList<SszKZGCommitment> kzgCommitments) {
      this.kzgCommitments = Optional.of(kzgCommitments);
      return this;
    }

    public BlockOptions setGenerateRandomBlobs(final boolean generateRandomBlobs) {
      this.generateRandomBlobs = generateRandomBlobs;
      return this;
    }

    public BlockOptions setStoreBlobsSidecar(final boolean storeBlobsSidecar) {
      this.storeBlobsSidecar = storeBlobsSidecar;
      return this;
    }

    public BlockOptions setExecutionPayload(final ExecutionPayload executionPayload) {
      this.executionPayload = Optional.of(executionPayload);
      return this;
    }

    public BlockOptions setSkipStateTransition(boolean skipStateTransition) {
      this.skipStateTransition = skipStateTransition;
      return this;
    }

    public BlockOptions setWrongProposer(boolean wrongProposer) {
      this.wrongProposer = wrongProposer;
      return this;
    }

    private List<Attestation> getAttestations() {
      return attestations;
    }

    public Optional<Eth1Data> getEth1Data() {
      return eth1Data;
    }

    public Optional<List<Bytes>> getTransactions() {
      return transactions;
    }

    public Optional<Bytes32> getTerminalBlockHash() {
      return terminalBlockHash;
    }

    public Optional<ExecutionPayload> getExecutionPayload() {
      return executionPayload;
    }

    public Optional<SszList<SignedBlsToExecutionChange>> getBlsToExecutionChange() {
      return blsToExecutionChange;
    }

    public Optional<SszList<SszKZGCommitment>> getKzgCommitments() {
      return kzgCommitments;
    }

    public Optional<List<Blob>> getBlobs() {
      return blobs;
    }

    public Optional<BlobsSidecar> getBlobsSidecar() {
      return blobsSidecar;
    }

    public boolean isStoreBlobsSidecarEnabled() {
      return storeBlobsSidecar;
    }

    public Optional<KZGProof> getKzgProof() {
      return kzgProof;
    }

    public boolean getSkipStateTransition() {
      return skipStateTransition;
    }

    public boolean getGenerateRandomBlobs() {
      return generateRandomBlobs;
    }

    public boolean getWrongProposer() {
      return wrongProposer;
    }

    public List<AttesterSlashing> getAttesterSlashings() {
      return attesterSlashings;
    }
  }
}
