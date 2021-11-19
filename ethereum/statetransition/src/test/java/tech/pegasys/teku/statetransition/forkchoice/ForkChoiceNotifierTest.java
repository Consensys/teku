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

  ExecutionEngineChannel executionEngineChannel = mock(ExecutionEngineChannel.class);

  @BeforeAll
  public static void init() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = false;
  }

  @AfterAll
  public static void reset() {
    AbstractBlockProcessor.BLS_VERIFY_DEPOSIT = true;
  }

  @BeforeEach
  void setUp(TestSpecInvocationContextProvider.SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    Spec spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
    recentChainData = storageSystem.recentChainData();
    chainUpdater = new ChainUpdater(storageSystem.recentChainData(), chainBuilder);
    chainUpdater.initializeGenesis(false);

    forkChoiceNotifier = new ForkChoiceNotifier(recentChainData, executionEngineChannel, spec);

    when(executionEngineChannel.forkChoiceUpdated(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ForkChoiceUpdatedResult(ForkChoiceUpdatedStatus.SUCCESS, Optional.empty())));
  }

  @TestTemplate
  void shouldCallForkChoiceWithAttributes() {
    prepareAllValidators();

    SignedBlockAndState signedBlockAndState = chainBuilder.getLatestBlockAndState();

    Bytes32 root = signedBlockAndState.getRoot();
    Bytes32 executionHeadRoot = dataStructureUtil.randomBytes32();
    ForkChoiceState forkChoiceState =
        new ForkChoiceState(
            executionHeadRoot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32());

    forkChoiceNotifier.onForkChoiceUpdated(root, forkChoiceState);

    verify(executionEngineChannel)
        .forkChoiceUpdated(forkChoiceStateCaptor.capture(), payloadAttributesCaptor.capture());

    assertThat(forkChoiceStateCaptor.getValue()).isEqualToComparingFieldByField(forkChoiceState);
    assertThat(payloadAttributesCaptor.getValue()).isNotEmpty();
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
}
