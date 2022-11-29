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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractSignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttesterSlashingValidator.AttesterSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.ProposerSlashingValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.spec.logic.common.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.synccommittee.SignedContributionAndProofValidator;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

class BlockOperationSelectorFactoryTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final OperationValidator<AttesterSlashing> attesterSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<ProposerSlashing> proposerSlashingValidator =
      mock(OperationValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationValidator<SignedVoluntaryExit> voluntaryExitValidator =
      mock(OperationValidator.class);

  private final SignedContributionAndProofValidator contributionValidator =
      mock(SignedContributionAndProofValidator.class);

  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final OperationPool<AttesterSlashing> attesterSlashingPool =
      new OperationPool<>(
          "attester_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
          attesterSlashingValidator,
          Comparator.<AttesterSlashing>comparingInt(
                  slashing -> slashing.getIntersectingValidatorIndices().size())
              .reversed());
  private final OperationPool<ProposerSlashing> proposerSlashingPool =
      new OperationPool<>(
          "proposer_slashing",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
          proposerSlashingValidator);
  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool =
      new OperationPool<>(
          "voluntary_exit",
          metricsSystem,
          beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
          voluntaryExitValidator);
  private final SyncCommitteeContributionPool contributionPool =
      new SyncCommitteeContributionPool(spec, contributionValidator);

  private final DepositProvider depositProvider = mock(DepositProvider.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final Bytes32 defaultGraffiti = dataStructureUtil.randomBytes32();
  private final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
  private final BLSSignature randaoReveal = dataStructureUtil.randomSignature();

  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);

  private final ExecutionPayload defaultExecutionPayload =
      SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
          .getExecutionPayloadSchema()
          .getDefault();

  private final ExecutionPayloadHeader executionPayloadHeaderOfDefaultPayload =
      SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
          .getExecutionPayloadHeaderSchema()
          .getHeaderOfDefaultPayload();

  private final CapturingBeaconBlockBodyBuilder bodyBuilder =
      new CapturingBeaconBlockBodyBuilder(false);

  private final CapturingBeaconBlockBodyBuilder blindedBodyBuilder =
      new CapturingBeaconBlockBodyBuilder(true);

  private final BlockOperationSelectorFactory factory =
      new BlockOperationSelectorFactory(
          spec,
          attestationPool,
          attesterSlashingPool,
          proposerSlashingPool,
          voluntaryExitPool,
          contributionPool,
          depositProvider,
          eth1DataCache,
          defaultGraffiti,
          forkChoiceNotifier,
          executionLayer);

  @BeforeEach
  void setUp() {
    when(attestationPool.getAttestationsForBlock(any(), any(), any()))
        .thenReturn(
            beaconBlockSchemaSupplier.apply(UInt64.ZERO).getAttestationsSchema().getDefault());
    when(contributionValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(attesterSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(proposerSlashingValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(voluntaryExitValidator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void shouldNotSelectOperationsWhenNoneAreAvailable() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(bodyBuilder);

    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).isEmpty();
    assertThat(bodyBuilder.voluntaryExits).isEmpty();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeBits().getBitCount()).isZero();
    assertThat(bodyBuilder.syncAggregate.getSyncCommitteeSignature().getSignature().isInfinity())
        .isTrue();
  }

  @Test
  void shouldIncludeValidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit = dataStructureUtil.randomSignedVoluntaryExit();
    final ProposerSlashing proposerSlashing = dataStructureUtil.randomProposerSlashing();
    final AttesterSlashing attesterSlashing = dataStructureUtil.randomAttesterSlashing();
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    addToPool(voluntaryExitPool, voluntaryExit);
    addToPool(proposerSlashingPool, proposerSlashing);
    addToPool(attesterSlashingPool, attesterSlashing);
    assertThat(contributionPool.addLocal(contribution)).isCompletedWithValue(ACCEPT);

    factory
        .createSelector(parentRoot, blockSlotState, randaoReveal, Optional.empty())
        .accept(bodyBuilder);

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).containsOnly(proposerSlashing);
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing);
    assertThat(bodyBuilder.voluntaryExits).containsOnly(voluntaryExit);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
  }

  private <T extends SszData> void addToPool(final OperationPool<T> pool, final T operation) {
    assertThat(pool.addRemote(operation)).isCompletedWithValue(ACCEPT);
  }

  @Test
  void shouldNotIncludeInvalidOperations() {
    final UInt64 slot = UInt64.valueOf(2);
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);
    final SignedVoluntaryExit voluntaryExit1 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(60));
    final SignedVoluntaryExit voluntaryExit2 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(65));
    final SignedVoluntaryExit voluntaryExit3 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final SignedVoluntaryExit voluntaryExit4 =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(69));
    final ProposerSlashing proposerSlashing1 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(60));
    final ProposerSlashing proposerSlashing2 =
        dataStructureUtil.randomProposerSlashing(UInt64.ONE, UInt64.valueOf(61));
    final AttesterSlashing attesterSlashing1 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(60), UInt64.valueOf(62));
    final AttesterSlashing attesterSlashing2 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(63), UInt64.valueOf(64));
    final AttesterSlashing attesterSlashing3 =
        dataStructureUtil.randomAttesterSlashing(UInt64.valueOf(62));
    final SignedContributionAndProof contribution =
        dataStructureUtil.randomSignedContributionAndProof(1, parentRoot);
    addToPool(voluntaryExitPool, voluntaryExit1);
    addToPool(voluntaryExitPool, voluntaryExit2);
    addToPool(voluntaryExitPool, voluntaryExit3);
    addToPool(voluntaryExitPool, voluntaryExit4);
    addToPool(proposerSlashingPool, proposerSlashing1);
    addToPool(proposerSlashingPool, proposerSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing1);
    addToPool(attesterSlashingPool, attesterSlashing2);
    addToPool(attesterSlashingPool, attesterSlashing3);
    assertThat(contributionPool.addRemote(contribution)).isCompletedWithValue(ACCEPT);

    when(proposerSlashingValidator.validateForBlockInclusion(blockSlotState, proposerSlashing2))
        .thenReturn(Optional.of(ProposerSlashingInvalidReason.INVALID_SIGNATURE));
    when(voluntaryExitValidator.validateForBlockInclusion(blockSlotState, voluntaryExit2))
        .thenReturn(Optional.of(ExitInvalidReason.invalidSignature()));
    when(attesterSlashingValidator.validateForBlockInclusion(blockSlotState, attesterSlashing2))
        .thenReturn(Optional.of(AttesterSlashingInvalidReason.ATTESTATIONS_NOT_SLASHABLE));

    factory
        .createSelector(parentRoot, blockSlotState, randaoReveal, Optional.empty())
        .accept(bodyBuilder);

    assertThat(bodyBuilder.randaoReveal).isEqualTo(randaoReveal);
    assertThat(bodyBuilder.graffiti).isEqualTo(defaultGraffiti);
    assertThat(bodyBuilder.proposerSlashings).isEmpty();
    assertThat(bodyBuilder.attesterSlashings).containsOnly(attesterSlashing1);
    // Both exit 3 or 4 are valid, but you can't include two exits for the same validator
    assertThat(bodyBuilder.voluntaryExits).hasSize(1).containsAnyOf(voluntaryExit3, voluntaryExit4);
    assertThat(bodyBuilder.syncAggregate)
        .isEqualTo(
            spec.getSyncCommitteeUtilRequired(slot)
                .createSyncAggregate(List.of(contribution.getMessage().getContribution())));
  }

  @Test
  void shouldIncludeDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconStatePreMerge(slot);
    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(bodyBuilder);
    assertThat(bodyBuilder.executionPayload).isEqualTo(defaultExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderOfDefaultPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconStatePreMerge(slot);
    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(blindedBodyBuilder);
    assertThat(blindedBodyBuilder.executionPayloadHeader)
        .isEqualTo(executionPayloadHeaderOfDefaultPayload);
  }

  @Test
  void shouldIncludeNonDefaultExecutionPayload() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    when(executionLayer.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(randomExecutionPayload));

    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(bodyBuilder);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderIfBlindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayloadHeader randomExecutionPayloadHeader =
        dataStructureUtil.randomExecutionPayloadHeader();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    when(executionLayer.builderGetHeader(executionPayloadContext, blockSlotState))
        .thenReturn(SafeFuture.completedFuture(randomExecutionPayloadHeader));

    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(blindedBodyBuilder);

    assertThat(blindedBodyBuilder.executionPayloadHeader).isEqualTo(randomExecutionPayloadHeader);
  }

  @Test
  void shouldIncludeExecutionPayloadIfNoBlindedBlockRequested() {
    final UInt64 slot = UInt64.ONE;
    final BeaconState blockSlotState = dataStructureUtil.randomBeaconState(slot);

    final ExecutionPayloadContext executionPayloadContext =
        dataStructureUtil.randomPayloadExecutionContext(false);
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();

    when(forkChoiceNotifier.getPayloadId(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayloadContext)));
    when(executionLayer.engineGetPayload(executionPayloadContext, slot))
        .thenReturn(SafeFuture.completedFuture(randomExecutionPayload));

    factory
        .createSelector(
            parentRoot, blockSlotState, dataStructureUtil.randomSignature(), Optional.empty())
        .accept(bodyBuilder);

    assertThat(bodyBuilder.executionPayload).isEqualTo(randomExecutionPayload);
  }

  @Test
  void shouldUnblindSignedBlindedBeaconBlock() {
    final ExecutionPayload randomExecutionPayload = dataStructureUtil.randomExecutionPayload();
    final SignedBeaconBlock blindedSignedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final CapturingBeaconBlockUnblinder blockUnblinder =
        new CapturingBeaconBlockUnblinder(spec.getGenesisSchemaDefinitions(), blindedSignedBlock);

    when(executionLayer.builderGetPayload(blindedSignedBlock))
        .thenReturn(SafeFuture.completedFuture(randomExecutionPayload));

    factory.createUnblinderSelector().accept(blockUnblinder);

    assertThat(blockUnblinder.executionPayload).isCompletedWithValue(randomExecutionPayload);
  }

  private static class CapturingBeaconBlockBodyBuilder implements BeaconBlockBodyBuilder {
    private final boolean blinded;

    protected BLSSignature randaoReveal;
    protected Bytes32 graffiti;
    protected SszList<ProposerSlashing> proposerSlashings;
    protected SszList<AttesterSlashing> attesterSlashings;
    protected SszList<SignedVoluntaryExit> voluntaryExits;
    protected SyncAggregate syncAggregate;
    protected ExecutionPayload executionPayload;
    protected ExecutionPayloadHeader executionPayloadHeader;

    public CapturingBeaconBlockBodyBuilder(boolean blinded) {
      this.blinded = blinded;
    }

    @Override
    public Boolean isBlinded() {
      return blinded;
    }

    @Override
    public BeaconBlockBodyBuilder randaoReveal(final BLSSignature randaoReveal) {
      this.randaoReveal = randaoReveal;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder eth1Data(final Eth1Data eth1Data) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder graffiti(final Bytes32 graffiti) {
      this.graffiti = graffiti;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attestations(final SszList<Attestation> attestations) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder proposerSlashings(
        final SszList<ProposerSlashing> proposerSlashings) {
      this.proposerSlashings = proposerSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder attesterSlashings(
        final SszList<AttesterSlashing> attesterSlashings) {
      this.attesterSlashings = attesterSlashings;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder deposits(final SszList<Deposit> deposits) {
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder voluntaryExits(
        final SszList<SignedVoluntaryExit> voluntaryExits) {
      this.voluntaryExits = voluntaryExits;
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder syncAggregate(
        final Supplier<SyncAggregate> syncAggregateSupplier) {
      this.syncAggregate = syncAggregateSupplier.get();
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayload(
        Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
      this.executionPayload = safeJoin(executionPayloadSupplier.get());
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder executionPayloadHeader(
        Supplier<SafeFuture<ExecutionPayloadHeader>> executionPayloadHeaderSupplier) {
      this.executionPayloadHeader = safeJoin(executionPayloadHeaderSupplier.get());
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder blsToExecutionChanges(
        Supplier<SszList<SignedBlsToExecutionChange>> blsToExecutionChanges) {
      // do nothing
      return this;
    }

    @Override
    public BeaconBlockBodyBuilder blobKzgCommitments(
        Supplier<SszList<SszKZGCommitment>> blobKzgCommitments) {
      // do nothing
      return this;
    }

    @Override
    public SafeFuture<BeaconBlockBody> build() {
      return null;
    }
  }

  private static class CapturingBeaconBlockUnblinder extends AbstractSignedBeaconBlockUnblinder {
    protected SafeFuture<ExecutionPayload> executionPayload;

    public CapturingBeaconBlockUnblinder(
        SchemaDefinitions schemaDefinitions, SignedBeaconBlock signedBlindedBeaconBlock) {
      super(schemaDefinitions, signedBlindedBeaconBlock);
    }

    @Override
    public void setExecutionPayloadSupplier(
        Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier) {
      this.executionPayload = executionPayloadSupplier.get();
    }

    @Override
    public SafeFuture<SignedBeaconBlock> unblind() {
      return null;
    }
  }
}
