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

package tech.pegasys.artemis.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists.createAttestations;

import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.events.SlotEvent;
import tech.pegasys.artemis.util.Waiter;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ValidatorCoordinatorTest {

  private BlockAttestationsPool blockAttestationsPool;
  private AttestationAggregator attestationAggregator;
  private EventBus eventBus;
  private ChainStorageClient storageClient;
  private ArtemisConfiguration config;
  private ValidatorCoordinator vc;

  private final int numValidators = 12;

  @BeforeEach
  void setup() {
    config = mock(ArtemisConfiguration.class);
    doReturn(0).when(config).getNaughtinessPercentage();
    doReturn(numValidators).when(config).getNumValidators();
    doReturn(null).when(config).getValidatorsKeyFile();
    doReturn(0).when(config).getInteropOwnedValidatorStartIndex();
    doReturn(numValidators).when(config).getInteropOwnedValidatorCount();

    attestationAggregator = mock(AttestationAggregator.class);
    blockAttestationsPool = mock(BlockAttestationsPool.class);
    doReturn(createAttestations())
        .when(blockAttestationsPool)
        .getAggregatedAttestationsForBlockAtSlot(any());

    eventBus = spy(new EventBus());
    storageClient = new ChainStorageClient(eventBus);
    List<BLSKeyPair> blsKeyPairList =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, numValidators);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(storageClient, blsKeyPairList);
    chainUtil.initializeStorage();

    vc =
        spy(
            new ValidatorCoordinator(
                eventBus, storageClient, attestationAggregator, blockAttestationsPool, config));
  }

  @Test
  void onAttestationEvent_noAttestationAssignments() throws Exception {
    doReturn(0).when(config).getInteropOwnedValidatorCount();
    vc =
        spy(
            new ValidatorCoordinator(
                eventBus, storageClient, attestationAggregator, blockAttestationsPool, config));
    eventBus.post(new BroadcastAttestationEvent(storageClient.getBestBlockRoot()));

    // Until the PR #1043 gets merged in that contains "ensureConditionRemainsMet"
    Thread.sleep(1000);
    verify(attestationAggregator, never()).updateAggregatorInformations(any());
    verify(vc, never()).asyncProduceAttestations(any(), any(), any());
  }

  @Test
  void createBlockAfterNormalSlot() {
    eventBus.post(new SlotEvent(storageClient.getBestSlot().plus(UnsignedLong.ONE)));
    ArgumentCaptor<BeaconBlock> blockArgumentCaptor = ArgumentCaptor.forClass(BeaconBlock.class);
    Waiter.waitFor(() -> verify(eventBus, atLeastOnce()).post(blockArgumentCaptor.capture()));

    assertThat(blockArgumentCaptor.getValue().getSlot())
        .isEqualByComparingTo(storageClient.getBestSlot().plus(UnsignedLong.ONE));
  }

  @Test
  void createBlockAfterSkippedSlot() {
    eventBus.post(new SlotEvent(storageClient.getBestSlot().plus(UnsignedLong.valueOf(2))));
    ArgumentCaptor<BeaconBlock> blockArgumentCaptor = ArgumentCaptor.forClass(BeaconBlock.class);
    Waiter.waitFor(() -> verify(eventBus, atLeastOnce()).post(blockArgumentCaptor.capture()));

    assertThat(blockArgumentCaptor.getValue().getSlot())
        .isEqualByComparingTo(storageClient.getBestSlot().plus(UnsignedLong.valueOf(2)));
  }

  @Test
  void createBlockAfterMultipleSkippedSlots() {
    eventBus.post(new SlotEvent(storageClient.getBestSlot().plus(UnsignedLong.valueOf(10))));
    ArgumentCaptor<BeaconBlock> blockArgumentCaptor = ArgumentCaptor.forClass(BeaconBlock.class);
    Waiter.waitFor(() -> verify(eventBus, atLeastOnce()).post(blockArgumentCaptor.capture()));

    assertThat(blockArgumentCaptor.getValue().getSlot())
        .isEqualByComparingTo(storageClient.getBestSlot().plus(UnsignedLong.valueOf(10)));
  }
}
