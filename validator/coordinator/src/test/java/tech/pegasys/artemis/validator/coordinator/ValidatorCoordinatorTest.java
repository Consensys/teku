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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists.createAttestations;
import static tech.pegasys.artemis.util.Waiter.ensureConditionRemainsMet;

import com.google.common.eventbus.EventBus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.events.attestation.BroadcastAttestationEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.api.ValidatorApiChannel;

public class ValidatorCoordinatorTest {

  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final BlockAttestationsPool blockAttestationsPool = mock(BlockAttestationsPool.class);
  private final AttestationAggregator attestationAggregator = mock(AttestationAggregator.class);
  private final ArtemisConfiguration config = mock(ArtemisConfiguration.class);
  private EventBus eventBus;
  private ChainStorageClient storageClient;
  private BeaconChainUtil chainUtil;

  private static final int NUM_VALIDATORS = 12;

  @BeforeEach
  void setup() {
    Constants.GENESIS_SLOT = 0;
    Constants.MIN_ATTESTATION_INCLUSION_DELAY = 0;
    when(config.getNumValidators()).thenReturn(NUM_VALIDATORS);
    when(config.getValidatorsKeyFile()).thenReturn(null);
    when(config.getValidatorKeystorePasswordFilePairs()).thenReturn(null);
    when(config.getInteropOwnedValidatorStartIndex()).thenReturn(0);
    when(config.getInteropOwnedValidatorCount()).thenReturn(NUM_VALIDATORS);

    when(blockAttestationsPool.getAttestationsForSlot(any())).thenReturn(createAttestations());

    eventBus = new EventBus();
    storageClient = ChainStorageClient.memoryOnlyClient(eventBus);
    List<BLSKeyPair> blsKeyPairList =
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, NUM_VALIDATORS);
    chainUtil = BeaconChainUtil.create(storageClient, blsKeyPairList);
  }

  @Test
  void onAttestationEvent_noAttestationAssignments() throws Exception {
    ValidatorCoordinator vc = spy(createValidatorCoordinator());
    eventBus.post(
        new BroadcastAttestationEvent(
            storageClient.getBestBlockRoot().orElseThrow(), storageClient.getBestSlot()));

    ensureConditionRemainsMet(
        () -> verify(attestationAggregator, never()).updateAggregatorInformations(any()));
    ensureConditionRemainsMet(
        () -> verify(vc, never()).asyncProduceAttestations(any(), any(), any()));
  }

  private ValidatorCoordinator createValidatorCoordinator() {
    when(config.getInteropOwnedValidatorCount()).thenReturn(0);
    ValidatorCoordinator vc =
        new ValidatorCoordinator(
            eventBus,
            validatorApiChannel,
            storageClient,
            attestationAggregator,
            blockAttestationsPool,
            eth1DataCache,
            config);

    chainUtil.initializeStorage();
    vc.start().reportExceptions();
    return vc;
  }
}
