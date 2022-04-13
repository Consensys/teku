/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.executionengine;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class ExecutionEngineChannelMock implements ExecutionEngineChannel {
  private static final Logger LOG = LogManager.getLogger();
  private final TimeProvider timeProvider;
  private final Map<Bytes32, PowBlock> knownBlocks = new ConcurrentHashMap<>();
  private final LRUCache<Bytes8, HeadAndAttributes> payloadIdToHeadAndAttrsCache;
  private final AtomicLong payloadIdCounter = new AtomicLong(0);
  private Set<Bytes32> requestedPowBlocks = new HashSet<>();
  private final Spec spec;
  private PayloadStatus payloadStatus = PayloadStatus.VALID;
  private ExecutionPayload lastPayloadToBeUnblinded;

  // transition emulation
  private final boolean transitionEmulationEnabled;
  private static final int TRANSITION_DELAY_AFTER_BELLATRIX_ACTIVATION = 10;
  private static final Bytes32 TERMINAL_BLOCK_PARENT_HASH = Bytes32.ZERO;
  private static final Bytes32 TERMINAL_BLOCK_HASH = Bytes32.fromHexStringLenient("0x01");
  private PowBlock terminalBlockParent;
  private PowBlock terminalBlock;
  private boolean terminalBlockSent;
  private UInt64 transitionTime;

  public ExecutionEngineChannelMock(
      final Spec spec, final TimeProvider timeProvider, final boolean enableTransitionEmulation) {
    this.payloadIdToHeadAndAttrsCache = LRUCache.create(10);
    this.spec = spec;
    this.timeProvider = timeProvider;
    this.transitionEmulationEnabled = enableTransitionEmulation;
    if (enableTransitionEmulation) {
      prepareTransitionBlocks();
    }
  }

  public ExecutionEngineChannelMock(final Spec spec, final boolean enableTransitionEmulation) {
    this(spec, new SystemTimeProvider(), enableTransitionEmulation);
  }

  public void addPowBlock(final PowBlock block) {
    knownBlocks.put(block.getBlockHash(), block);
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    if (!transitionEmulationEnabled) {
      requestedPowBlocks.add(blockHash);
      return SafeFuture.completedFuture(Optional.ofNullable(knownBlocks.get(blockHash)));
    }
    if (blockHash.equals(TERMINAL_BLOCK_PARENT_HASH)) {
      return SafeFuture.completedFuture(Optional.of(terminalBlockParent));
    }
    return SafeFuture.failedFuture(
        new UnsupportedOperationException("getPowBlock supported for terminalBlockParent only"));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    if (!transitionEmulationEnabled) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException("getPowChainHead not supported"));
    }
    if (terminalBlockSent) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException("getPowChainHead not supported after transition"));
    }
    if (timeProvider.getTimeInSeconds().isGreaterThanOrEqualTo(transitionTime)) {
      terminalBlockSent = true;
      return SafeFuture.completedFuture(terminalBlock);
    }
    return SafeFuture.completedFuture(terminalBlockParent);
  }

  @Override
  public SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<PayloadAttributes> payloadAttributes) {
    return SafeFuture.completedFuture(
        new ForkChoiceUpdatedResult(
            PayloadStatus.VALID,
            payloadAttributes.map(
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
  public SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId, final UInt64 slot) {
    Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        spec.atSlot(slot).getSchemaDefinitions().toVersionBellatrix();

    if (schemaDefinitionsBellatrix.isEmpty()) {
      return SafeFuture.failedFuture(
          new UnsupportedOperationException(
              "getPayload not supported for non-Bellatrix milestones"));
    }

    Optional<HeadAndAttributes> maybeHeadAndAttrs =
        payloadIdToHeadAndAttrsCache.getCached(payloadId);
    if (maybeHeadAndAttrs.isEmpty()) {
      return SafeFuture.failedFuture(new RuntimeException("payloadId not found in cache"));
    }

    HeadAndAttributes headAndAttrs = maybeHeadAndAttrs.get();
    PayloadAttributes payloadAttributes = headAndAttrs.attributes;

    return SafeFuture.completedFuture(
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
                List.of(Bytes.fromHexString("0x0edf"), Bytes.fromHexString("0xedf0"))));
  }

  @Override
  public SafeFuture<PayloadStatus> newPayload(final ExecutionPayload executionPayload) {
    return SafeFuture.completedFuture(payloadStatus);
  }

  @Override
  public SafeFuture<TransitionConfiguration> exchangeTransitionConfiguration(
      TransitionConfiguration transitionConfiguration) {
    return SafeFuture.completedFuture(transitionConfiguration);
  }

  @Override
  public SafeFuture<ExecutionPayloadHeader> getPayloadHeader(Bytes8 payloadId, UInt64 slot) {
    return getPayload(payloadId, slot)
        .thenApply(
            executionPayload -> {
              lastPayloadToBeUnblinded = executionPayload;
              return spec.atSlot(slot)
                  .getSchemaDefinitions()
                  .toVersionBellatrix()
                  .orElseThrow()
                  .getExecutionPayloadHeaderSchema()
                  .createFromExecutionPayload(executionPayload);
            });
  }

  @Override
  public SafeFuture<ExecutionPayload> proposeBlindedBlock(
      SignedBeaconBlock signedBlindedBeaconBlock) {
    final Optional<SchemaDefinitionsBellatrix> schemaDefinitionsBellatrix =
        spec.atSlot(signedBlindedBeaconBlock.getSlot()).getSchemaDefinitions().toVersionBellatrix();

    checkState(
        schemaDefinitionsBellatrix.isPresent(),
        "proposeBlindedBlock not supported for non-Bellatrix milestones");

    checkState(
        signedBlindedBeaconBlock.getBeaconBlock().orElseThrow().getBody().isBlinded(),
        "proposeBlindedBlock requires a signed blinded beacon block");

    checkNotNull(
        lastPayloadToBeUnblinded,
        "proposeBlindedBlock requires a previous call to getPayloadHeader");

    final ExecutionPayloadHeader executionPayloadHeader =
        signedBlindedBeaconBlock
            .getBeaconBlock()
            .orElseThrow()
            .getBody()
            .getOptionalExecutionPayloadHeader()
            .orElseThrow();

    checkState(
        executionPayloadHeader.hashTreeRoot().equals(lastPayloadToBeUnblinded.hashTreeRoot()),
        "provided signed blinded block contains an execution payload header not matching the previously retrieved execution payload via getPayloadHeader");

    return SafeFuture.completedFuture(lastPayloadToBeUnblinded);
  }

  public PayloadStatus getPayloadStatus() {
    return payloadStatus;
  }

  public void setPayloadStatus(PayloadStatus payloadStatus) {
    this.payloadStatus = payloadStatus;
  }

  public Set<Bytes32> getRequestedPowBlocks() {
    return requestedPowBlocks;
  }

  private static class HeadAndAttributes {
    private final Bytes32 head;
    private final PayloadAttributes attributes;

    private HeadAndAttributes(Bytes32 head, PayloadAttributes attributes) {
      this.head = head;
      this.attributes = attributes;
    }
  }

  private void prepareTransitionBlocks() {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.BELLATRIX);
    checkNotNull(specVersion, "Bellatrix must be scheduled to for transition emulation");
    final SpecConfigBellatrix specConfigBellatrix =
        specVersion.getConfig().toVersionBellatrix().orElseThrow();

    final UInt64 bellatrixActivationSlot =
        spec.computeStartSlotAtEpoch(specConfigBellatrix.getBellatrixForkEpoch());

    // assumes genesis happen immediately at min genesis time reached
    final UInt64 genesisTime =
        spec.getSlotStartTime(
            bellatrixActivationSlot, spec.getGenesisSpecConfig().getMinGenesisTime());

    transitionTime = genesisTime.plus(TRANSITION_DELAY_AFTER_BELLATRIX_ACTIVATION);

    terminalBlockParent =
        new PowBlock(TERMINAL_BLOCK_PARENT_HASH, Bytes32.ZERO, UInt256.ZERO, UInt64.ZERO);
    terminalBlock =
        new PowBlock(
            TERMINAL_BLOCK_HASH,
            TERMINAL_BLOCK_PARENT_HASH,
            specConfigBellatrix.getTerminalTotalDifficulty(),
            transitionTime);

    final UInt64 currentTime = timeProvider.getTimeInSeconds();

    // if transition time is passed, we assume terminalBlock already sent
    terminalBlockSent = currentTime.isGreaterThanOrEqualTo(transitionTime);
    if (terminalBlockSent) {
      LOG.info("transition assumed to be already happened.");
    }
  }
}
