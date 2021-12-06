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

package tech.pegasys.teku.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.util.config.Constants.MIN_ATTESTATION_INCLUSION_DELAY;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.AttestationGenerator;
import tech.pegasys.teku.core.BlockProposalTestUtil;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionengine.StubExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.util.config.Constants;

/** @deprecated Prefer ChainBuilder, ChainUpdater, or StorageSystem */
@Deprecated
public class BeaconChainUtil {
  // TODO(#3356) Inject spec provider rather than using this default
  private static final Spec DEFAULT_SPEC_PROVIDER = TestSpecFactory.createMinimalPhase0();

  private final Spec spec;
  private final BeaconBlockBodySchema<?> beaconBlockBodySchema;
  private final BlockProposalTestUtil blockCreator;
  private final RecentChainData recentChainData;
  private final ForkChoice forkChoice;
  private final List<BLSKeyPair> validatorKeys;
  private final boolean signDeposits;

  private BeaconChainUtil(
      final Spec spec,
      final List<BLSKeyPair> validatorKeys,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      boolean signDeposits) {
    this.spec = spec;
    this.beaconBlockBodySchema =
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema();
    this.validatorKeys = validatorKeys;
    this.recentChainData = recentChainData;
    this.signDeposits = signDeposits;
    this.forkChoice = forkChoice;
    this.blockCreator = new BlockProposalTestUtil(spec);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Deprecated
  public static BeaconChainUtil create(
      final int validatorCount, final RecentChainData storageClient) {
    return create(DEFAULT_SPEC_PROVIDER, validatorCount, storageClient);
  }

  public static BeaconChainUtil create(
      final Spec spec, final int validatorCount, final RecentChainData storageClient) {
    final List<BLSKeyPair> validatorKeys =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);
    return create(spec, storageClient, validatorKeys);
  }

  @Deprecated
  public static BeaconChainUtil create(
      final RecentChainData storageClient, final List<BLSKeyPair> validatorKeys) {
    return create(DEFAULT_SPEC_PROVIDER, storageClient, validatorKeys);
  }

  public static BeaconChainUtil create(
      final Spec spec, final RecentChainData storageClient, final List<BLSKeyPair> validatorKeys) {
    return create(
        spec,
        storageClient,
        validatorKeys,
        ForkChoice.create(
            spec, new InlineEventThread(), storageClient, mock(ForkChoiceNotifier.class)),
        true);
  }

  @Deprecated
  public static BeaconChainUtil create(
      final RecentChainData storageClient,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    return create(DEFAULT_SPEC_PROVIDER, storageClient, validatorKeys, signDeposits);
  }

  public static BeaconChainUtil create(
      final Spec spec,
      final RecentChainData storageClient,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    return new BeaconChainUtil(
        spec,
        validatorKeys,
        storageClient,
        ForkChoice.create(
            spec, new InlineEventThread(), storageClient, mock(ForkChoiceNotifier.class)),
        signDeposits);
  }

  public static BeaconChainUtil create(
      final Spec spec,
      final RecentChainData storageClient,
      final List<BLSKeyPair> validatorKeys,
      final ForkChoice forkChoice,
      final boolean signDeposits) {
    return new BeaconChainUtil(spec, validatorKeys, storageClient, forkChoice, signDeposits);
  }

  public static void initializeStorage(
      final Spec spec,
      final RecentChainData recentChainData,
      final List<BLSKeyPair> validatorKeys) {
    initializeStorage(spec, recentChainData, validatorKeys, true);
  }

  public static void initializeStorage(
      final Spec spec,
      final RecentChainData recentChainData,
      final List<BLSKeyPair> validatorKeys,
      final boolean signDeposits) {
    final BeaconState initState =
        InteropStartupUtil.createMockedStartInitialBeaconState(
            spec, 0, validatorKeys, signDeposits);
    recentChainData.initializeFromGenesis(initState, UInt64.ZERO);
  }

  public void initializeStorage() {
    initializeStorage(recentChainData);
  }

  public void initializeStorage(final RecentChainData recentChainData) {
    initializeStorage(spec, recentChainData, validatorKeys, signDeposits);
  }

  public void setSlot(final UInt64 currentSlot) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set current slot before genesis");
    final UInt64 secPerSlot = UInt64.valueOf(Constants.SECONDS_PER_SLOT);
    final UInt64 time = recentChainData.getGenesisTime().plus(currentSlot.times(secPerSlot));
    setTime(time);
  }

  public void setTime(final UInt64 time) {
    checkState(!recentChainData.isPreGenesis(), "Cannot set time before genesis");
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setTime(time);
    tx.commit().join();
  }

  public SignedBeaconBlock createBlockAtSlot(final UInt64 slot) throws Exception {
    return createBlockAtSlot(slot, true);
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final long slot) throws Exception {
    return createAndImportBlockAtSlot(UInt64.valueOf(slot));
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithExits(
      final UInt64 slot, List<SignedVoluntaryExit> exits) throws Exception {
    Optional<SszList<SignedVoluntaryExit>> exitsSSZList =
        exits.isEmpty()
            ? Optional.empty()
            : Optional.of(
                beaconBlockBodySchema.getVoluntaryExitsSchema().createFromElements(exits));

    return createAndImportBlockAtSlot(
        slot, Optional.empty(), Optional.empty(), exitsSSZList, Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithDeposits(
      final UInt64 slot, List<Deposit> deposits) throws Exception {
    Optional<SszList<Deposit>> depositsSSZlist =
        deposits.isEmpty()
            ? Optional.empty()
            : Optional.of(beaconBlockBodySchema.getDepositsSchema().createFromElements(deposits));

    return createAndImportBlockAtSlot(
        slot, Optional.empty(), depositsSSZlist, Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlotWithAttestations(
      final UInt64 slot, List<Attestation> attestations) throws Exception {
    Optional<SszList<Attestation>> attestationsSSZList =
        attestations.isEmpty()
            ? Optional.empty()
            : Optional.of(
                beaconBlockBodySchema.getAttestationsSchema().createFromElements(attestations));

    return createAndImportBlockAtSlot(
        slot, attestationsSSZList, Optional.empty(), Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(
      final UInt64 slot,
      Optional<SszList<Attestation>> attestations,
      Optional<SszList<Deposit>> deposits,
      Optional<SszList<SignedVoluntaryExit>> exits,
      Optional<Eth1Data> eth1Data)
      throws Exception {
    final SignedBeaconBlock block =
        createBlockAndStateAtSlot(slot, true, attestations, deposits, exits, eth1Data).getBlock();
    setSlot(slot);
    final BlockImportResult importResult =
        forkChoice.onBlock(block, new StubExecutionEngineChannel(spec)).join();
    if (!importResult.isSuccessful()) {
      throw new IllegalStateException(
          "Produced an invalid block ( reason "
              + importResult.getFailureReason().name()
              + ") at slot "
              + slot
              + ": "
              + block);
    }
    forkChoice.processHead(slot).join();
    return importResult.getBlock();
  }

  public SignedBeaconBlock createAndImportBlockAtSlot(final UInt64 slot) throws Exception {
    return createAndImportBlockAtSlot(
        slot, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public SignedBeaconBlock createBlockAtSlotFromInvalidProposer(final UInt64 slot)
      throws Exception {
    return createBlockAtSlot(slot, false);
  }

  public SignedBeaconBlock createBlockAtSlot(final UInt64 slot, boolean withValidProposer)
      throws Exception {
    return createBlockAndStateAtSlot(slot, withValidProposer).getBlock();
  }

  public SignedBlockAndState createBlockAndStateAtSlot(final UInt64 slot, boolean withValidProposer)
      throws Exception {
    return createBlockAndStateAtSlot(
        slot,
        withValidProposer,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private SignedBlockAndState createBlockAndStateAtSlot(
      final UInt64 slot,
      boolean withValidProposer,
      Optional<SszList<Attestation>> attestations,
      Optional<SszList<Deposit>> deposits,
      Optional<SszList<SignedVoluntaryExit>> exits,
      Optional<Eth1Data> eth1Data)
      throws Exception {
    checkState(
        withValidProposer || validatorKeys.size() > 1,
        "Must have >1 validator in order to create a block from an invalid proposer.");
    final StateAndBlockSummary bestBlockAndState = recentChainData.getChainHead().orElseThrow();
    final Bytes32 bestBlockRoot = bestBlockAndState.getRoot();
    final BeaconState preState = bestBlockAndState.getState();
    checkArgument(bestBlockAndState.getSlot().compareTo(slot) < 0, "Slot must be in the future.");

    final int correctProposerIndex = blockCreator.getProposerIndexForSlot(preState, slot);
    final int proposerIndex =
        withValidProposer ? correctProposerIndex : getWrongProposerIndex(correctProposerIndex);

    final Signer signer = getSigner(proposerIndex);
    return blockCreator.createBlock(
        signer,
        slot,
        preState,
        bestBlockRoot,
        attestations,
        deposits,
        exits,
        eth1Data,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        false);
  }

  public void finalizeChainAtEpoch(final UInt64 epoch) throws Exception {
    if (recentChainData.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) >= 0) {
      throw new Exception("Chain already finalized at this or higher epoch");
    }

    AttestationGenerator attestationGenerator = new AttestationGenerator(spec, validatorKeys);
    createAndImportBlockAtSlot(recentChainData.getHeadSlot().plus(MIN_ATTESTATION_INCLUSION_DELAY));

    while (recentChainData.getStore().getFinalizedCheckpoint().getEpoch().compareTo(epoch) < 0) {

      StateAndBlockSummary head = recentChainData.getChainHead().orElseThrow();
      UInt64 slot = recentChainData.getHeadSlot();
      SszList<Attestation> currentSlotAssignments =
          beaconBlockBodySchema
              .getAttestationsSchema()
              .createFromElements(attestationGenerator.getAttestationsForSlot(head, slot));
      createAndImportBlockAtSlot(
          recentChainData.getHeadSlot().plus(UInt64.ONE),
          Optional.of(currentSlotAssignments),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }
  }

  public List<BLSKeyPair> getValidatorKeys() {
    return validatorKeys;
  }

  public int getWrongProposerIndex(final int actualProposerIndex) {
    return actualProposerIndex == 0 ? 1 : actualProposerIndex - 1;
  }

  public Signer getSigner(final int proposerIndex) {
    return new LocalSigner(spec, validatorKeys.get(proposerIndex), SYNC_RUNNER);
  }

  public static class Builder {
    // Required
    private RecentChainData recentChainData;
    // Not required
    private Spec spec = TestSpecFactory.createMinimalPhase0();
    private Integer validatorCount = 3;
    private Boolean signDeposits = true;
    private ForkChoice forkChoice;
    private List<BLSKeyPair> validatorKeys;

    public BeaconChainUtil build() {
      validate();
      if (forkChoice == null) {
        final InlineEventThread forkChoiceExecutor = new InlineEventThread();
        forkChoice =
            ForkChoice.create(
                spec, forkChoiceExecutor, recentChainData, mock(ForkChoiceNotifier.class));
      }
      if (validatorKeys == null) {
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount);
      }
      return new BeaconChainUtil(spec, validatorKeys, recentChainData, forkChoice, signDeposits);
    }

    private void validate() {
      checkNotNull(recentChainData);
    }

    public Builder recentChainData(final RecentChainData recentChainData) {
      checkNotNull(recentChainData);
      this.recentChainData = recentChainData;
      return this;
    }

    public Builder specProvider(final Spec spec) {
      checkNotNull(spec);
      this.spec = spec;
      return this;
    }

    public Builder validatorCount(final Integer validatorCount) {
      checkNotNull(validatorCount);
      this.validatorCount = validatorCount;
      return this;
    }

    public Builder signDeposits(final Boolean signDeposits) {
      checkNotNull(signDeposits);
      this.signDeposits = signDeposits;
      return this;
    }

    public Builder forkChoice(final ForkChoice forkChoice) {
      checkNotNull(forkChoice);
      this.forkChoice = forkChoice;
      return this;
    }

    public Builder validatorKeys(final List<BLSKeyPair> validatorKeys) {
      checkNotNull(validatorKeys);
      this.validatorKeys = validatorKeys;
      return this;
    }
  }
}
