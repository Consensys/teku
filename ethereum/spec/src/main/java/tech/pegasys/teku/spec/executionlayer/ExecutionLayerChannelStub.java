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

package tech.pegasys.teku.spec.executionlayer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionLayerChannelStub implements ExecutionLayerChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final TimeProvider timeProvider;
  private final Map<Bytes32, PowBlock> knownBlocks = new ConcurrentHashMap<>();
  private final LRUCache<Bytes8, HeadAndAttributes> payloadIdToHeadAndAttrsCache;
  private final AtomicLong payloadIdCounter = new AtomicLong(0);
  private final Set<Bytes32> requestedPowBlocks = new HashSet<>();
  private final Spec spec;
  private PayloadStatus payloadStatus = PayloadStatus.VALID;

  // transition emulation
  private static final int TRANSITION_DELAY_AFTER_BELLATRIX_ACTIVATION = 10;
  private static final Bytes32 TERMINAL_BLOCK_PARENT_HASH = Bytes32.ZERO;
  private final boolean transitionEmulationEnabled;
  private final Bytes32 terminalBlockHashInTTDMode;
  private boolean bellatrixActivationDetected = false;
  private Bytes32 terminalBlockHash;
  private PowBlock terminalBlockParent;
  private PowBlock terminalBlock;
  private boolean terminalBlockSent;
  private UInt64 transitionTime;
  private Optional<TransitionConfiguration> transitionConfiguration = Optional.empty();

  // block and payload tracking
  private Optional<ExecutionPayload> lastBuilderPayloadToBeUnblinded = Optional.empty();
  private Optional<PowBlock> lastValidBlock = Optional.empty();

  public ExecutionLayerChannelStub(
      final Spec spec,
      final TimeProvider timeProvider,
      final boolean enableTransitionEmulation,
      final Optional<Bytes32> terminalBlockHashInTTDMode) {
    this.payloadIdToHeadAndAttrsCache = LRUCache.create(10);
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.transitionEmulationEnabled = enableTransitionEmulation;
    this.terminalBlockHashInTTDMode =
        terminalBlockHashInTTDMode.orElse(Bytes32.fromHexStringLenient("0x01"));
  }

  public ExecutionLayerChannelStub(
      final Spec spec,
      final boolean enableTransitionEmulation,
      final Optional<Bytes32> terminalBlockHashInTTDMode) {
    this(spec, new SystemTimeProvider(), enableTransitionEmulation, terminalBlockHashInTTDMode);
  }

  public void addPowBlock(final PowBlock block) {
    knownBlocks.put(block.getBlockHash(), block);
  }

  @Override
  public SafeFuture<Optional<PowBlock>> eth1GetPowBlock(final Bytes32 blockHash) {
    if (!transitionEmulationEnabled) {
      requestedPowBlocks.add(blockHash);
      return SafeFuture.completedFuture(Optional.ofNullable(knownBlocks.get(blockHash)));
    }

    checkBellatrixActivation();

    if (blockHash.equals(TERMINAL_BLOCK_PARENT_HASH)) {
      return SafeFuture.completedFuture(Optional.of(terminalBlockParent));
    }
    if (blockHash.equals(terminalBlockHash)) {
      // TBH flow
      LOG.info("TBH: sending terminal block hash " + terminalBlockHash);
      terminalBlockSent = true;
      return SafeFuture.completedFuture(Optional.of(terminalBlock));
    }

    return SafeFuture.failedFuture(
        new UnsupportedOperationException(
            "getPowBlock supported for terminalBlockParent or terminalBlock only. Requested block: "
                + blockHash));
  }

  @Override
  public SafeFuture<PowBlock> eth1GetPowChainHead() {
    if (!transitionEmulationEnabled) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException("getPowChainHead not supported"));
    }

    checkBellatrixActivation();

    if (terminalBlockSent) {
      return SafeFuture.completedFuture(lastValidBlock.orElse(terminalBlock));
    }
    if (timeProvider.getTimeInSeconds().isGreaterThanOrEqualTo(transitionTime)) {
      // TTD flow
      LOG.info("TTD: sending terminal block hash " + terminalBlockHash);
      terminalBlockSent = true;
      return SafeFuture.completedFuture(terminalBlock);
    }
    return SafeFuture.completedFuture(terminalBlockParent);
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    if (!bellatrixActivationDetected) {
      LOG.info(
          "forkChoiceUpdated received before terminalBlock has been sent. Assuming transition already happened");

      // do the activation check to be able to respond to terminal block verification
      checkBellatrixActivation();
    }

    return SafeFuture.completedFuture(
        new ForkChoiceUpdatedResult(
            PayloadStatus.VALID,
            payloadBuildingAttributes.map(
                payloadAttributes1 -> {
                  Bytes8 payloadId =
                      Bytes8.leftPad(Bytes.ofUnsignedInt(payloadIdCounter.incrementAndGet()));
                  payloadIdToHeadAndAttrsCache.invalidateWithNewValue(
                      payloadId,
                      new HeadAndAttributes(
                          forkChoiceState.getHeadExecutionBlockHash(), payloadAttributes1));
                  return payloadId;
                })));
  }

  @Override
  public SafeFuture<ExecutionPayload> engineGetPayload(
      final ExecutionPayloadContext executionPayloadContext, final UInt64 slot) {
    if (!bellatrixActivationDetected) {
      LOG.info(
          "getPayload received before terminalBlock has been sent. Assuming transition already happened");

      // do the activation check to be able to respond to terminal block verification
      checkBellatrixActivation();
    }

    final Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        spec.atSlot(slot).getSchemaDefinitions().toVersionBellatrix();

    if (schemaDefinitionsBellatrix.isEmpty()) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException(
              "getPayload not supported for non-Bellatrix milestones"));
    }

    final Optional<HeadAndAttributes> maybeHeadAndAttrs =
        payloadIdToHeadAndAttrsCache.getCached(executionPayloadContext.getPayloadId());
    if (maybeHeadAndAttrs.isEmpty()) {
      return SafeFuture.failedFuture(new RuntimeException("payloadId not found in cache"));
    }

    final HeadAndAttributes headAndAttrs = maybeHeadAndAttrs.get();
    final PayloadBuildingAttributes payloadAttributes = headAndAttrs.attributes;

    final ExecutionPayload executionPayload =
        schemaDefinitionsBellatrix
            .get()
            .getExecutionPayloadSchema()
            .create(
                headAndAttrs.head,
                payloadAttributes.getFeeRecipient(),
                Bytes32.ZERO,
                Bytes32.ZERO,
                Bytes.random(256),
                payloadAttributes.getPrevRandao(),
                UInt64.valueOf(payloadIdCounter.get()),
                UInt64.ONE,
                UInt64.ZERO,
                payloadAttributes.getTimestamp(),
                Bytes.EMPTY,
                UInt256.ONE,
                Bytes32.random(),
                List.of(Bytes.fromHexString("0x0edf"), Bytes.fromHexString("0xedf0")));

    // we assume all blocks are produced locally
    lastValidBlock =
        Optional.of(
            new PowBlock(
                executionPayload.getBlockHash(),
                executionPayload.getParentHash(),
                UInt256.ZERO,
                payloadAttributes.getTimestamp()));

    LOG.info(
        "getPayload: payloadId: {} slot: {} -> executionPayload blockHash: {}",
        executionPayloadContext.getPayloadId(),
        slot,
        executionPayload.getBlockHash());

    return SafeFuture.completedFuture(executionPayload);
  }

  @Override
  public SafeFuture<PayloadStatus> engineNewPayload(final ExecutionPayload executionPayload) {
    LOG.info(
        "newPayload: executionPayload blockHash: {} -> {}",
        executionPayload.getBlockHash(),
        payloadStatus);
    return SafeFuture.completedFuture(payloadStatus);
  }

  @Override
  public SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      final TransitionConfiguration transitionConfiguration) {
    final TransitionConfiguration transitionConfigurationResponse;

    this.transitionConfiguration = Optional.of(transitionConfiguration);

    if (transitionConfiguration.getTerminalBlockHash().isZero()) {
      transitionConfigurationResponse = transitionConfiguration;
    } else {
      transitionConfigurationResponse =
          new TransitionConfiguration(
              transitionConfiguration.getTerminalTotalDifficulty(),
              transitionConfiguration.getTerminalBlockHash(),
              UInt64.ONE);
    }
    LOG.info(
        "exchangeTransitionConfiguration: {} -> {}",
        transitionConfiguration,
        transitionConfigurationResponse);
    return SafeFuture.completedFuture(transitionConfigurationResponse);
  }

  @Override
  public SafeFuture<Void> builderRegisterValidators(
      final SszList<SignedValidatorRegistration> signedValidatorRegistrations, final UInt64 slot) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> builderGetHeader(
      final ExecutionPayloadContext executionPayloadContext, final BeaconState state) {
    final UInt64 slot = state.getSlot();
    LOG.info(
        "getPayloadHeader: payloadId: {} slot: {} ... delegating to getPayload ...",
        executionPayloadContext,
        slot);

    return engineGetPayload(executionPayloadContext, slot)
        .thenApply(
            executionPayload -> {
              LOG.info(
                  "getPayloadHeader: payloadId: {} slot: {} -> executionPayload blockHash: {}",
                  executionPayloadContext,
                  slot,
                  executionPayload.getBlockHash());
              lastBuilderPayloadToBeUnblinded = Optional.of(executionPayload);
              return spec.atSlot(slot)
                  .getSchemaDefinitions()
                  .toVersionBellatrix()
                  .orElseThrow()
                  .getExecutionPayloadHeaderSchema()
                  .createFromExecutionPayload(executionPayload);
            });
  }

  @Override
  public SafeFuture<ExecutionPayload> builderGetPayload(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    final Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        spec.atSlot(signedBlindedBeaconBlock.getSlot()).getSchemaDefinitions().toVersionBellatrix();

    checkState(
        schemaDefinitionsBellatrix.isPresent(),
        "proposeBlindedBlock not supported for non-Bellatrix milestones");

    checkState(
        signedBlindedBeaconBlock.getBeaconBlock().orElseThrow().getBody().isBlinded(),
        "proposeBlindedBlock requires a signed blinded beacon block");

    checkState(
        lastBuilderPayloadToBeUnblinded.isPresent(),
        "proposeBlindedBlock requires a previous call to getPayloadHeader");

    final ExecutionPayloadHeader executionPayloadHeader =
        signedBlindedBeaconBlock
            .getBeaconBlock()
            .orElseThrow()
            .getBody()
            .getOptionalExecutionPayloadHeader()
            .orElseThrow();

    checkState(
        executionPayloadHeader
            .hashTreeRoot()
            .equals(lastBuilderPayloadToBeUnblinded.get().hashTreeRoot()),
        "provided signed blinded block contains an execution payload header not matching the previously retrieved execution payload via getPayloadHeader");

    LOG.info(
        "proposeBlindedBlock: slot: {} block: {} -> unblinded executionPayload blockHash: {}",
        signedBlindedBeaconBlock.getSlot(),
        signedBlindedBeaconBlock.getRoot(),
        lastBuilderPayloadToBeUnblinded.get().getBlockHash());

    return SafeFuture.completedFuture(lastBuilderPayloadToBeUnblinded.get());
  }

  public PayloadStatus getPayloadStatus() {
    return payloadStatus;
  }

  public void setPayloadStatus(PayloadStatus payloadStatus) {
    this.payloadStatus = payloadStatus;
  }

  public Set<Bytes32> getRequestedPowBlocks() {
    return Collections.unmodifiableSet(requestedPowBlocks);
  }

  private static class HeadAndAttributes {
    private final Bytes32 head;
    private final PayloadBuildingAttributes attributes;

    private HeadAndAttributes(Bytes32 head, PayloadBuildingAttributes attributes) {
      this.head = head;
      this.attributes = attributes;
    }
  }

  private void checkBellatrixActivation() {
    if (!bellatrixActivationDetected) {
      LOG.info("Bellatrix activation detected");
      bellatrixActivationDetected = true;
      prepareTransitionBlocks(timeProvider.getTimeInSeconds());
    }
  }

  private void prepareTransitionBlocks(final UInt64 bellatrixActivationTime) {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.BELLATRIX);
    checkNotNull(specVersion, "Bellatrix must be scheduled to for transition emulation");
    final SpecConfigBellatrix specConfigBellatrix =
        specVersion.getConfig().toVersionBellatrix().orElseThrow();

    final Bytes32 configTerminalBlockHash;
    final UInt256 terminalTotalDifficulty;

    // let's try to use last received transition configuration, otherwise fallback to spec
    // we can't wait for transitionConfiguration because we may receive it too late,
    // so we may not be able to respond do transition block validation
    if (transitionConfiguration.isPresent()) {
      LOG.info("Preparing transition blocks using received transitionConfiguration");
      configTerminalBlockHash = transitionConfiguration.get().getTerminalBlockHash();
      terminalTotalDifficulty = transitionConfiguration.get().getTerminalTotalDifficulty();
    } else {
      LOG.info("Preparing transition blocks using spec");
      configTerminalBlockHash = specConfigBellatrix.getTerminalBlockHash();
      terminalTotalDifficulty = specConfigBellatrix.getTerminalTotalDifficulty();
    }

    if (configTerminalBlockHash.isZero()) {
      // TTD emulation
      LOG.info("Transition via TTD: {}", terminalTotalDifficulty);

      transitionTime = bellatrixActivationTime.plus(TRANSITION_DELAY_AFTER_BELLATRIX_ACTIVATION);

      terminalBlockHash = terminalBlockHashInTTDMode;

    } else {
      // TBH emulation
      LOG.info("Preparing transition via TBH: {}", configTerminalBlockHash);

      // transition time is not relevant, just wait for the getPowBlock asking for the transition
      // block
      transitionTime = bellatrixActivationTime;
      terminalBlockHash = configTerminalBlockHash;
    }

    terminalBlockParent =
        new PowBlock(TERMINAL_BLOCK_PARENT_HASH, Bytes32.ZERO, UInt256.ZERO, UInt64.ZERO);
    terminalBlock =
        new PowBlock(
            terminalBlockHash, TERMINAL_BLOCK_PARENT_HASH, terminalTotalDifficulty, transitionTime);
  }
}
