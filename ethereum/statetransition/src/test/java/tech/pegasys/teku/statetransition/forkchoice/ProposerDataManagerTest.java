/*
 * Copyright 2022 ConsenSys AG.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager.ProposersDataManagerSubscriber;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerDataManagerTest implements ProposersDataManagerSubscriber {
  private final InlineEventThread eventThread = new InlineEventThread();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final Spec specMock = mock(Spec.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannel executionLayerChannel = mock(ExecutionLayerChannel.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final Optional<Eth1Address> defaultFeeRecipient =
      Optional.of(Eth1Address.fromHexString("0x2Df386eFF130f991321bfC4F8372Ba838b9AB14B"));

  private final ProposersDataManager proposersDataManager =
      new ProposersDataManager(
          eventThread, specMock, executionLayerChannel, recentChainData, defaultFeeRecipient);

  private final BeaconState state = dataStructureUtil.randomBeaconState();

  private boolean onValidatorRegistrationsUpdatedCalled = false;

  private final UInt64 slot = UInt64.ONE;
  private SszList<SignedValidatorRegistration> registrations;
  private final SafeFuture<Void> response = new SafeFuture<>();

  @Test
  void shouldCallRegisterValidator() {

    prepareRegistrations();

    final SafeFuture<Void> updateCall = proposersDataManager.updateValidatorRegistrations(registrations, slot);

    assertThat(onValidatorRegistrationsUpdatedCalled).isFalse();
    verify(executionLayerChannel).builderRegisterValidators(registrations, slot);
    verifyNoMoreInteractions(executionLayerChannel);

    response.complete(null);

    assertThat(updateCall).isCompleted();

    // final update
    assertThat(onValidatorRegistrationsUpdatedCalled).isTrue();
  }

  @Test
  void shouldNotSignalValidatorRegistrationUpdatedOnError() {

    prepareRegistrations();

    final SafeFuture<Void> updateCall = proposersDataManager.updateValidatorRegistrations(registrations, slot);

    assertThat(onValidatorRegistrationsUpdatedCalled).isFalse();
    verify(executionLayerChannel).builderRegisterValidators(registrations, slot);

    response.completeExceptionally(new RuntimeException("generic error"));

    assertThat(updateCall).isCompletedExceptionally();

    assertThat(onValidatorRegistrationsUpdatedCalled).isFalse();
    verifyNoMoreInteractions(executionLayerChannel);
  }

  private void prepareRegistrations() {
    registrations = dataStructureUtil.randomSignedValidatorRegistrations(2);

    when(executionLayerChannel.builderRegisterValidators(registrations, slot)).thenReturn(response);
    when(recentChainData.getBestState()).thenReturn(Optional.of(SafeFuture.completedFuture(state)));
    when(specMock.getValidatorIndex(state, registrations.get(0).getMessage().getPublicKey()))
        .thenReturn(Optional.of(0));
    when(specMock.getValidatorIndex(state, registrations.get(1).getMessage().getPublicKey()))
        .thenReturn(Optional.of(1));

    proposersDataManager.subscribeToProposersDataChanges(this);
  }

  @Override
  public void onPreparedProposersUpdated() {}

  @Override
  public void onValidatorRegistrationsUpdated() {
    onValidatorRegistrationsUpdatedCalled = true;
  }
}
