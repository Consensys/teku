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

package tech.pegasys.teku.validator.relaypublisher;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;

class MultiPublishingBeaconNodeApiTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil data = new DataStructureUtil(spec);

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger = mock(Logger.class);

  private final BeaconNodeApi delegate = mock(BeaconNodeApi.class);
  private final ValidatorApiChannel validator = mock(ValidatorApiChannel.class);
  private final List<BeaconNodeApi> publishers =
      List.of(mock(BeaconNodeApi.class), mock(BeaconNodeApi.class));
  private final List<ValidatorApiChannel> publishersValidatorChannel =
      List.of(mock(ValidatorApiChannel.class), mock(ValidatorApiChannel.class));

  @BeforeEach
  void setup() {
    when(delegate.getValidatorApi()).thenReturn(validator);
    for (int i = 0; i < publishers.size(); i++) {
      when(publishers.get(i).getValidatorApi()).thenReturn(publishersValidatorChannel.get(i));
    }
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToEvents() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.subscribeToEvents());
    verify(delegate, times(1)).subscribeToEvents();
    verifyNoMoreInteractions(delegate);
    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_unsubscribeFromEvents() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.unsubscribeFromEvents());
    verify(delegate, times(1)).unsubscribeFromEvents();
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldGetMultiPublishObject_getValidatorApi() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    assertThat(api.getValidatorApi()).isEqualTo(api);
  }

  @Test
  void shouldOnlyCallDelegate_getValidatorIndices() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getValidatorIndices(Collections.emptyList()));
    verify(validator, times(1)).getValidatorIndices(Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getGenesisData() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getGenesisData());
    verify(validator, times(1)).getGenesisData();
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getValidatorStatuses() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getValidatorStatuses(Collections.emptyList()));
    verify(validator, times(1)).getValidatorStatuses(Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getAttestationDuties() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getAttestationDuties(UInt64.ONE, Collections.emptyList()));
    verify(validator, times(1)).getAttestationDuties(UInt64.ONE, Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getSyncCommitteeDuties() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getSyncCommitteeDuties(UInt64.ONE, Collections.emptyList()));
    verify(validator, times(1)).getSyncCommitteeDuties(UInt64.ONE, Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getProposerDuties() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.getProposerDuties(UInt64.ONE));
    verify(validator, times(1)).getProposerDuties(UInt64.ONE);
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createUnsignedBlock() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.createUnsignedBlock(UInt64.ONE, BLSSignature.empty(), Optional.empty()));
    verify(validator, times(1))
        .createUnsignedBlock(UInt64.ONE, BLSSignature.empty(), Optional.empty());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createAttestationData() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.createAttestationData(UInt64.ONE, 1));
    verify(validator, times(1)).createAttestationData(UInt64.ONE, 1);
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createAggregate() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.createAggregate(UInt64.ONE, Bytes32.ZERO));
    verify(validator, times(1)).createAggregate(UInt64.ONE, Bytes32.ZERO);
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createSyncCommitteeContribution() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    ignoreFuture(api.createSyncCommitteeContribution(UInt64.ONE, 1, Bytes32.ZERO));
    verify(validator, times(1)).createSyncCommitteeContribution(UInt64.ONE, 1, Bytes32.ZERO);
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToBeaconCommittee() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    api.subscribeToBeaconCommittee(Collections.emptyList());
    verify(validator, times(1)).subscribeToBeaconCommittee(Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToSyncCommitteeSubnets() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    api.subscribeToSyncCommitteeSubnets(Collections.emptyList());
    verify(validator, times(1)).subscribeToSyncCommitteeSubnets(Collections.emptyList());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToPersistentSubnets() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);

    api.subscribeToPersistentSubnets(Collections.emptySet());
    verify(validator, times(1)).subscribeToPersistentSubnets(Collections.emptySet());
    verifyNoMoreInteractions(validator);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedAttestations() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);
    final List<Attestation> attestationsList = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedAttestations(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendSignedAttestations(attestationsList));
    verify(validator, times(1)).sendSignedAttestations(attestationsList);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSignedAttestations(attestationsList));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedAttestations() {

    MultiPublishingBeaconNodeApi api =
        new MultiPublishingBeaconNodeApi(delegate, publishers, logger);
    final List<Attestation> attestationsList = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedAttestations(anyList()))
                .thenReturn(
                    SafeFuture.failedFuture(new IllegalStateException("computer says no"))));

    ignoreFuture(api.sendSignedAttestations(attestationsList));
    verify(validator, times(1)).sendSignedAttestations(attestationsList);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSignedAttestations(attestationsList));
    verify(logger, times(2)).warn(anyString(), ArgumentMatchers.eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendAggregateAndProofs() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);
    final List<SignedAggregateAndProof> signedAggregateAndProofs = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendAggregateAndProofs(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendAggregateAndProofs(signedAggregateAndProofs));
    verify(validator, times(1)).sendAggregateAndProofs(signedAggregateAndProofs);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendAggregateAndProofs(signedAggregateAndProofs));
  }

  @Test
  void shouldLogPublisherWarnings_sendAggregateAndProofs() {
    MultiPublishingBeaconNodeApi api =
        new MultiPublishingBeaconNodeApi(delegate, publishers, logger);
    final List<SignedAggregateAndProof> signedAggregateAndProofs = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendAggregateAndProofs(anyList()))
                .thenReturn(
                    SafeFuture.failedFuture(new IllegalStateException("computer says no"))));

    ignoreFuture(api.sendAggregateAndProofs(signedAggregateAndProofs));
    verify(validator, times(1)).sendAggregateAndProofs(signedAggregateAndProofs);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendAggregateAndProofs(signedAggregateAndProofs));
    verify(logger, times(2)).warn(anyString(), ArgumentMatchers.eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedBlock() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(1);
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedBlock(block))
                .thenReturn(SafeFuture.completedFuture(mock(SendSignedBlockResult.class))));

    ignoreFuture(api.sendSignedBlock(block));
    verify(validator, times(1)).sendSignedBlock(block);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(val -> verify(val, times(1)).sendSignedBlock(block));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedBlock() {
    MultiPublishingBeaconNodeApi api =
        new MultiPublishingBeaconNodeApi(delegate, publishers, logger);
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(1);
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedBlock(block))
                .thenReturn(
                    SafeFuture.failedFuture(new IllegalStateException("computer says no"))));

    ignoreFuture(api.sendSignedBlock(block));
    verify(validator, times(1)).sendSignedBlock(block);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(val -> verify(val, times(1)).sendSignedBlock(block));
    verify(logger, times(2)).warn(anyString(), ArgumentMatchers.eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSyncCommitteeMessages() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);
    final List<SyncCommitteeMessage> syncCommitteeMessages = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSyncCommitteeMessages(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(validator, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages));
  }

  @Test
  void shouldLogPublisherWarnings_sendSyncCommitteeMessages() {
    MultiPublishingBeaconNodeApi api =
        new MultiPublishingBeaconNodeApi(delegate, publishers, logger);
    final List<SyncCommitteeMessage> syncCommitteeMessages = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSyncCommitteeMessages(anyList()))
                .thenReturn(
                    SafeFuture.failedFuture(new IllegalStateException("computer says no"))));

    ignoreFuture(api.sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(validator, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(logger, times(2)).warn(anyString(), ArgumentMatchers.eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedContributionAndProofs() {
    MultiPublishingBeaconNodeApi api = new MultiPublishingBeaconNodeApi(delegate, publishers);
    final List<SignedContributionAndProof> signedContributionAndProofs = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedContributionAndProofs(anyList())).thenReturn(SafeFuture.COMPLETE));

    ignoreFuture(api.sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(validator, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedContributionAndProofs() {
    MultiPublishingBeaconNodeApi api =
        new MultiPublishingBeaconNodeApi(delegate, publishers, logger);
    final List<SignedContributionAndProof> signedContributionAndProofs = Collections.emptyList();
    publishersValidatorChannel.forEach(
        val ->
            when(val.sendSignedContributionAndProofs(anyList()))
                .thenReturn(
                    SafeFuture.failedFuture(new IllegalStateException("computer says no"))));

    ignoreFuture(api.sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(validator, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs);
    verifyNoMoreInteractions(validator);

    publishersValidatorChannel.forEach(
        val -> verify(val, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(logger, times(2)).warn(anyString(), ArgumentMatchers.eq("computer says no"));
  }
}
