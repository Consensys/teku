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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.versions.merge.BeaconPreparableProposer;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.spec.executionengine.ForkChoiceState;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionengine.ForkChoiceUpdatedStatus;
import tech.pegasys.teku.spec.executionengine.PayloadAttributes;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.ssz.type.Bytes20;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext()
public class ForkChoiceNotifierTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(64);

  final ArgumentCaptor<ForkChoiceState> forkChoiceStateCaptor =
      ArgumentCaptor.forClass(ForkChoiceState.class);

  @SuppressWarnings("unchecked")
  final ArgumentCaptor<Optional<PayloadAttributes>> payloadAttributesCaptor =
      ArgumentCaptor.forClass(Optional.class);

  ChainBuilder chainBuilder;
  StorageSystem storageSystem;
  ChainUpdater chainUpdater;
  RecentChainData recentChainData;
  ForkChoiceNotifier forkChoiceNotifier;
  DataStructureUtil dataStructureUtil;
  ForkChoiceState forkChoiceState;
  Bytes32 root;
  Spec spec;

  ExecutionEngineChannel executionEngineChannel = mock(ExecutionEngineChannel.class);

  @BeforeAll
  public static void initSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void resetSession() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  void setUp(TestSpecInvocationContextProvider.SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
    recentChainData = storageSystem.recentChainData();
    chainUpdater = new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
    chainUpdater.initializeGenesis(false);

    forkChoiceNotifier = new ForkChoiceNotifier(recentChainData, executionEngineChannel, spec);

    resetExecutionEngineChannelMock();
  }

  @TestTemplate
  void onForkChoiceShouldCallForkChoiceUpdatedWithAttributesWhenProposerIsPrepared() {
    prepareAllValidators();

    setRootAndForkChoiceState();

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    validateForkChoiceUpdatedWithPayloadAttributes(chainBuilder.getLatestSlot().plus(1));
  }

  @TestTemplate
  void onForkChoiceShouldCallForkChoiceUpdatedWithoutAttributesWhenProposerIsNotPrepared() {
    setRootAndForkChoiceState();

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    validateForkChoiceUpdatedWithoutPayloadAttributes(chainBuilder.getLatestSlot().plus(1));
  }

  @TestTemplate
  void onUpdatePreparableProposersShouldNotCallForkChoiceUpdatedWithNotForkChoiceState() {
    prepareAllValidators();

    validateForkChoiceUpdatedHasNotBeenCalled();
  }

  @TestTemplate
  void onUpdatePreparableProposersShouldNotCallForkChoiceUpdatedWhenNotPreparingProposer() {
    setRootAndForkChoiceState();

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    resetExecutionEngineChannelMock();

    prepareWithNonProposingValidators();

    validateForkChoiceUpdatedHasNotBeenCalled();
  }

  @TestTemplate
  void onUpdatePreparableProposersShouldCallForkChoiceUpdatedOnCurrentSlotWhenPreparingProposer() {
    setRootAndForkChoiceState();

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    resetExecutionEngineChannelMock();

    prepareAllValidators();

    validateForkChoiceUpdatedWithPayloadAttributes(chainBuilder.getLatestSlot());
  }

  @TestTemplate
  void
      onUpdatePreparableProposersShouldCallForkChoiceUpdatedOnNextSlotWhenPreparingProposerAfterAttestationDue() {
    setRootAndForkChoiceState();

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);
    forkChoiceNotifier.onAttestationsDue(chainBuilder.getLatestSlot());

    resetExecutionEngineChannelMock();

    prepareAllValidators();

    validateForkChoiceUpdatedWithPayloadAttributes(chainBuilder.getLatestSlot().plus(1));
  }

  @TestTemplate
  void complexScenario1() {
    setRootAndForkChoiceState();

    // we begin with no proposers
    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    validateForkChoiceUpdatedWithoutPayloadAttributes(chainBuilder.getLatestSlot().plus(1));

    resetExecutionEngineChannelMock();

    // then, before attestationDue, we prepare validator
    prepareAllValidators();

    validateForkChoiceUpdatedWithPayloadAttributes(chainBuilder.getLatestSlot());

    resetExecutionEngineChannelMock();

    // then forkchoice state changes before attestationDue
    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    validateForkChoiceUpdatedWithPayloadAttributes(chainBuilder.getLatestSlot().plus(1));

    resetExecutionEngineChannelMock();

    // attestationDue arrives

    forkChoiceNotifier.onAttestationsDue(chainBuilder.getLatestSlot());

    validateForkChoiceUpdatedHasNotBeenCalled();
  }

  private void prepareAllValidators() {
    Collection<BeaconPreparableProposer> proposers =
        IntStream.range(0, VALIDATOR_KEYS.size())
            .mapToObj(
                index ->
                    new BeaconPreparableProposer(
                        UInt64.valueOf(index), dataStructureUtil.randomBytes20()))
            .collect(Collectors.toList());

    forkChoiceNotifier.onUpdatePreparableProposers(proposers);
  }

  private void prepareWithNonProposingValidators() {
    Collection<BeaconPreparableProposer> proposers =
        IntStream.range(VALIDATOR_KEYS.size() + 1, VALIDATOR_KEYS.size() + 5)
            .mapToObj(
                index ->
                    new BeaconPreparableProposer(
                        UInt64.valueOf(index), dataStructureUtil.randomBytes20()))
            .collect(Collectors.toList());

    forkChoiceNotifier.onUpdatePreparableProposers(proposers);
  }

  private void validateForkChoiceUpdatedHasNotBeenCalled() {
    verify(executionEngineChannel, never())
        .forkChoiceUpdated(forkChoiceStateCaptor.capture(), payloadAttributesCaptor.capture());
  }

  private void validateForkChoiceUpdatedWithPayloadAttributes(UInt64 targetSlot) {
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceStateCaptor.capture(), payloadAttributesCaptor.capture());

    assertThat(forkChoiceStateCaptor.getValue()).isEqualToComparingFieldByField(forkChoiceState);

    SignedBlockAndState signedBlockAndState = chainBuilder.getLatestBlockAndState();
    UInt64 expectedProposerIndex =
        UInt64.valueOf(spec.getBeaconProposerIndex(signedBlockAndState.getState(), targetSlot));
    Bytes20 proposerLastSeenSlotAndFeeRecipient =
        forkChoiceNotifier.getProposerIndexFeeRecipient(expectedProposerIndex);
    assertThat(proposerLastSeenSlotAndFeeRecipient).isNotNull();

    Optional<PayloadAttributes> payloadAttributes = payloadAttributesCaptor.getValue();
    assertThat(payloadAttributes).isNotEmpty();
    assertThat(payloadAttributes.get().getFeeRecipient())
        .isEqualTo(proposerLastSeenSlotAndFeeRecipient);
  }

  private void validateForkChoiceUpdatedWithoutPayloadAttributes(UInt64 targetSlot) {
    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceStateCaptor.capture(), payloadAttributesCaptor.capture());

    assertThat(forkChoiceStateCaptor.getValue()).isEqualToComparingFieldByField(forkChoiceState);

    SignedBlockAndState signedBlockAndState = chainBuilder.getLatestBlockAndState();
    UInt64 expectedProposerIndex =
        UInt64.valueOf(spec.getBeaconProposerIndex(signedBlockAndState.getState(), targetSlot));
    Bytes20 proposerLastSeenSlotAndFeeRecipient =
        forkChoiceNotifier.getProposerIndexFeeRecipient(expectedProposerIndex);
    assertThat(proposerLastSeenSlotAndFeeRecipient).isNull();

    Optional<PayloadAttributes> payloadAttributes = payloadAttributesCaptor.getValue();
    assertThat(payloadAttributes).isEmpty();
  }

  private void resetExecutionEngineChannelMock() {
    reset(executionEngineChannel);
    when(executionEngineChannel.forkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.empty())));
  }

  private void setRootAndForkChoiceState() {
    SignedBlockAndState signedBlockAndState = chainBuilder.getLatestBlockAndState();
    root = signedBlockAndState.getRoot();
    Bytes32 executionHeadRoot = dataStructureUtil.randomBytes32();
    forkChoiceState =
        new ForkChoiceState(
            executionHeadRoot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());
  }
}
