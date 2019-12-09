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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.statetransition.AttestationAggregator;
import tech.pegasys.artemis.statetransition.BeaconChainUtil;
import tech.pegasys.artemis.statetransition.BlockAttestationsPool;
import tech.pegasys.artemis.statetransition.events.BroadcastAttestationEvent;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class ValidatorCoordinatorTest {

  private ValidatorCoordinator vc;

  @Test
  void noValidators_noAttestationProduced() throws Exception {
    int numValidators = 0;

    ArtemisConfiguration config = mock(ArtemisConfiguration.class);
    doReturn(0).when(config).getNaughtinessPercentage();
    doReturn(numValidators).when(config).getNumValidators();
    doReturn(null).when(config).getValidatorsKeyFile();
    doReturn(0).when(config).getInteropOwnedValidatorStartIndex();
    doReturn(numValidators).when(config).getInteropOwnedValidatorCount();

    EventBus eventBus = spy(new EventBus());
    AttestationAggregator attestationAggregator = mock(AttestationAggregator.class);
    BlockAttestationsPool blockAttestationsPool = mock(BlockAttestationsPool.class);
    final ChainStorageClient storageClient = new ChainStorageClient(eventBus);
    final BeaconChainUtil chainUtil = BeaconChainUtil.create(numValidators, storageClient);
    chainUtil.initializeStorage();

    vc =
        spy(
            new ValidatorCoordinator(
                eventBus, storageClient, attestationAggregator, blockAttestationsPool, config));

    eventBus.post(
        new BroadcastAttestationEvent(
            storageClient.getBestBlockRoot(), storageClient.getBestSlot()));

    // Until the PR #1043 gets merged in that contains "ensureConditionRemainsMet"
    Thread.sleep(1000);
    verify(attestationAggregator, never()).updateAggregatorInformations(any());
    verify(vc, never()).asyncProduceAttestations(any(), any(), any());
  }
}
