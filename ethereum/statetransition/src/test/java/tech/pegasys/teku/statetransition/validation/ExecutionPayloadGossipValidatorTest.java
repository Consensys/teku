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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteListImpl;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(
    milestone = {SpecMilestone.GLOAS},
    signatureVerifierNoop = true)
public class ExecutionPayloadGossipValidatorTest {

  private Spec spec;
  private RecentChainData recentChainData;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private ChainUpdater chainUpdater;
  private GossipValidationHelper gossipValidationHelper;
  private ExecutionPayloadGossipValidator executionPayloadGossipValidator;
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots =
      LimitedMap.createSynchronizedLRU(50);

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    chainBuilder = storageSystem.chainBuilder();
    chainUpdater = storageSystem.chainUpdater();
    recentChainData = storageSystem.recentChainData();
    gossipValidationHelper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
    executionPayloadGossipValidator =
        new ExecutionPayloadGossipValidator(spec, gossipValidationHelper, invalidBlockRoots);
  }

  @TestTemplate
  void shouldAcceptWhenValid() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelope))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @TestTemplate
  void shouldIgnoreIfAlreadySeen() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelope))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelope))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldSaveForFutureIfBlockNotSeen() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelopeWithBadBlockRoot =
        createExecutionPayloadEnvelopeWithBlockRoot(
            signedExecutionPayloadEnvelope, Bytes32.random());
    assertThat(
            executionPayloadGossipValidator.validate(
                signedExecutionPayloadEnvelopeWithBadBlockRoot))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldIgnoreIfSlotIsGreaterOrEqualToFinalized() {
    final UInt64 finalizedEpoch = ONE;
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    chainUpdater.advanceChain(finalizedSlot);
    chainUpdater.finalizeEpoch(finalizedEpoch);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(finalizedSlot).executionPayload();
    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelope))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldRejectIfBlockItselfIsInvalid() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final Bytes32 blockRoot = signedExecutionPayloadEnvelope.getBeaconBlockRoot();

    invalidBlockRoots.put(blockRoot, BlockImportResult.FAILED_BROADCAST_VALIDATION);

    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelope))
        .isCompletedWithValue(
            InternalValidationResult.reject(
                "Execution payload envelope's block with root %s is invalid", blockRoot));
  }

  @TestTemplate
  void shouldRejectIfPayloadSlotIsDifferentFromBlockSlot() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelopeWithBadSlot =
        createExecutionPayloadEnvelopeWithSlot(signedExecutionPayloadEnvelope, UInt64.valueOf(2));
    assertThat(executionPayloadGossipValidator.validate(signedExecutionPayloadEnvelopeWithBadSlot))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .contains(
                            String.format(
                                "SignedExecutionPayloadEnvelope slot %s does not match block slot %s.",
                                signedExecutionPayloadEnvelopeWithBadSlot.getSlot(),
                                signedExecutionPayloadEnvelope.getSlot())));
  }

  @TestTemplate
  void shouldRejectIfPayloadBuilderIndexIsDifferentFromBlockBidBuilderIndex() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelopeWithBadBuilderIndex =
        createExecutionPayloadEnvelopeWithBuilderIndex(
            signedExecutionPayloadEnvelope,
            signedExecutionPayloadEnvelope.getMessage().getBuilderIndex().increment());
    assertThat(
            executionPayloadGossipValidator.validate(
                signedExecutionPayloadEnvelopeWithBadBuilderIndex))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .contains(
                            String.format(
                                "Invalid builder index. Execution Payload Envelope had %s but the block Execution Payload Bid had %s",
                                signedExecutionPayloadEnvelopeWithBadBuilderIndex
                                    .getMessage()
                                    .getBuilderIndex(),
                                signedExecutionPayloadEnvelope.getMessage().getBuilderIndex())));
  }

  @TestTemplate
  void shouldRejectIfPayloadBlockHashIsDifferentFromBidBlockHash() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelopeWithBadBlockHash =
        createExecutionPayloadEnvelopeWithBlockHash(
            signedExecutionPayloadEnvelope, Bytes32.random());
    assertThat(
            executionPayloadGossipValidator.validate(
                signedExecutionPayloadEnvelopeWithBadBlockHash))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .contains(
                            String.format(
                                "Invalid payload block hash. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                                signedExecutionPayloadEnvelopeWithBadBlockHash
                                    .getMessage()
                                    .getPayload()
                                    .getBlockHash(),
                                signedExecutionPayloadEnvelope
                                    .getMessage()
                                    .getPayload()
                                    .getBlockHash())));
  }

  @TestTemplate
  void shouldRejectIfPayloadSignatureIsInvalid() {
    chainUpdater.advanceChain(ONE);
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelope =
        chainBuilder.getExecutionPayloadAndStateAtSlot(ONE).executionPayload();
    final SignedExecutionPayloadEnvelope signedExecutionPayloadEnvelopeWithBadSignature =
        createExecutionPayloadEnvelopeWithSignature(
            signedExecutionPayloadEnvelope, BLSSignature.empty());
    assertThat(
            executionPayloadGossipValidator.validate(
                signedExecutionPayloadEnvelopeWithBadSignature))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .contains("Invalid SignedExecutionPayloadEnvelope signature"));
  }

  private SignedExecutionPayloadEnvelope createModifiedExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelope original,
      final Optional<ExecutionPayload> payload,
      final Optional<UInt64> builderIndex,
      final Optional<Bytes32> beaconBlockRoot,
      final Optional<UInt64> slot,
      final Optional<BLSSignature> signature) {
    final SchemaDefinitionsGloas schemaDefinitions =
        spec.getGenesisSchemaDefinitions().toVersionGloas().orElseThrow();
    final ExecutionPayloadEnvelopeSchema envelopeSchema =
        schemaDefinitions.getExecutionPayloadEnvelopeSchema();
    final SignedExecutionPayloadEnvelopeSchema signedEnvelopeSchema =
        schemaDefinitions.getSignedExecutionPayloadEnvelopeSchema();
    final ExecutionPayloadEnvelope originalMessage = original.getMessage();
    final ExecutionPayloadEnvelope modifiedMessage =
        envelopeSchema.create(
            payload.orElse(originalMessage.getPayload()),
            originalMessage.getExecutionRequests(),
            builderIndex.orElse(originalMessage.getBuilderIndex()),
            beaconBlockRoot.orElse(originalMessage.getBeaconBlockRoot()),
            slot.orElse(originalMessage.getSlot()),
            originalMessage.getBlobKzgCommitments(),
            originalMessage.getStateRoot());
    return signedEnvelopeSchema.create(modifiedMessage, signature.orElse(original.getSignature()));
  }

  private SignedExecutionPayloadEnvelope createExecutionPayloadEnvelopeWithBuilderIndex(
      final SignedExecutionPayloadEnvelope envelope, final UInt64 builderIndex) {
    return createModifiedExecutionPayloadEnvelope(
        envelope,
        Optional.empty(),
        Optional.of(builderIndex),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private SignedExecutionPayloadEnvelope createExecutionPayloadEnvelopeWithSlot(
      final SignedExecutionPayloadEnvelope envelope, final UInt64 slot) {
    return createModifiedExecutionPayloadEnvelope(
        envelope,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(slot),
        Optional.empty());
  }

  private SignedExecutionPayloadEnvelope createExecutionPayloadEnvelopeWithBlockRoot(
      final SignedExecutionPayloadEnvelope envelope, final Bytes32 blockRoot) {
    return createModifiedExecutionPayloadEnvelope(
        envelope,
        Optional.empty(),
        Optional.empty(),
        Optional.of(blockRoot),
        Optional.empty(),
        Optional.empty());
  }

  private SignedExecutionPayloadEnvelope createExecutionPayloadEnvelopeWithSignature(
      final SignedExecutionPayloadEnvelope envelope, final BLSSignature signature) {
    return createModifiedExecutionPayloadEnvelope(
        envelope,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.of(signature));
  }

  private ExecutionPayload createExecutionPayloadWithBlockHash(
      final ExecutionPayload original, final Bytes32 blockHash) {
    final SchemaDefinitionsGloas schemaDefinitions =
        spec.getGenesisSchemaDefinitions().toVersionGloas().orElseThrow();
    return schemaDefinitions
        .getExecutionPayloadSchema()
        .createExecutionPayload(
            builder ->
                builder
                    .parentHash(original.getParentHash())
                    .feeRecipient(original.getFeeRecipient())
                    .stateRoot(original.getStateRoot())
                    .receiptsRoot(original.getReceiptsRoot())
                    .logsBloom(original.getLogsBloom())
                    .prevRandao(original.getPrevRandao())
                    .blockNumber(original.getBlockNumber())
                    .gasLimit(original.getGasLimit())
                    .gasUsed(original.getGasUsed())
                    .timestamp(original.getTimestamp())
                    .extraData(original.getExtraData())
                    .baseFeePerGas(original.getBaseFeePerGas())
                    .blockHash(blockHash)
                    .transactions(
                        original.getTransactions().stream().map(SszByteListImpl::getBytes).toList())
                    .withdrawals(() -> original.getOptionalWithdrawals().orElseThrow().asList())
                    .blobGasUsed(() -> UInt64.ZERO)
                    .excessBlobGas(() -> UInt64.ZERO));
  }

  private SignedExecutionPayloadEnvelope createExecutionPayloadEnvelopeWithBlockHash(
      final SignedExecutionPayloadEnvelope envelope, final Bytes32 blockHash) {
    final ExecutionPayload newPayload =
        createExecutionPayloadWithBlockHash(envelope.getMessage().getPayload(), blockHash);
    return createModifiedExecutionPayloadEnvelope(
        envelope,
        Optional.of(newPayload),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
