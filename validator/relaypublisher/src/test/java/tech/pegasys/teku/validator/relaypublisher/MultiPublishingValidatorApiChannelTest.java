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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.junit.jupiter.api.Test;
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
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class MultiPublishingValidatorApiChannelTest {
  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger logger = mock(Logger.class);

  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final ValidatorApiChannel delegate = mock(ValidatorApiChannel.class);
  private final List<AdditionalPublisherApi> publishers =
      List.of(mock(AdditionalPublisherApi.class), mock(AdditionalPublisherApi.class));

  @Test
  void shouldOnlyCallDelegate_getValidatorIndices() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getValidatorIndices(Collections.emptyList()));
    verify(delegate, times(1)).getValidatorIndices(Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getGenesisData() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getGenesisData());
    verify(delegate, times(1)).getGenesisData();
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getValidatorStatuses() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getValidatorStatuses(Collections.emptyList()));
    verify(delegate, times(1)).getValidatorStatuses(Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getAttestationDuties() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getAttestationDuties(UInt64.ONE, Collections.emptyList()));
    verify(delegate, times(1)).getAttestationDuties(UInt64.ONE, Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getSyncCommitteeDuties() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getSyncCommitteeDuties(UInt64.ONE, Collections.emptyList()));
    verify(delegate, times(1)).getSyncCommitteeDuties(UInt64.ONE, Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_getProposerDuties() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.getProposerDuties(UInt64.ONE));
    verify(delegate, times(1)).getProposerDuties(UInt64.ONE);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createUnsignedBlock() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.createUnsignedBlock(UInt64.ONE, BLSSignature.empty(), Optional.empty()));
    verify(delegate, times(1))
        .createUnsignedBlock(UInt64.ONE, BLSSignature.empty(), Optional.empty());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createAttestationData() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.createAttestationData(UInt64.ONE, 1));
    verify(delegate, times(1)).createAttestationData(UInt64.ONE, 1);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createAggregate() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.createAggregate(UInt64.ONE, Bytes32.ZERO));
    verify(delegate, times(1)).createAggregate(UInt64.ONE, Bytes32.ZERO);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_createSyncCommitteeContribution() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    ignoreFuture(api.createSyncCommitteeContribution(UInt64.ONE, 1, Bytes32.ZERO));
    verify(delegate, times(1)).createSyncCommitteeContribution(UInt64.ONE, 1, Bytes32.ZERO);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToBeaconCommittee() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    api.subscribeToBeaconCommittee(Collections.emptyList());
    verify(delegate, times(1)).subscribeToBeaconCommittee(Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToSyncCommitteeSubnets() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    api.subscribeToSyncCommitteeSubnets(Collections.emptyList());
    verify(delegate, times(1)).subscribeToSyncCommitteeSubnets(Collections.emptyList());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldOnlyCallDelegate_subscribeToPersistentSubnets() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);

    api.subscribeToPersistentSubnets(Collections.emptySet());
    verify(delegate, times(1)).subscribeToPersistentSubnets(Collections.emptySet());
    verifyNoMoreInteractions(delegate);

    publishers.forEach(Mockito::verifyNoInteractions);
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedAttestations() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);
    final List<Attestation> attestationsList = Collections.emptyList();
    publishers.forEach(
        val ->
            when(val.sendSignedAttestations(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendSignedAttestations(attestationsList));
    verify(delegate, times(1)).sendSignedAttestations(attestationsList);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(val -> verify(val, times(1)).sendSignedAttestations(attestationsList));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedAttestations() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers, logger);

    final List<Attestation> attestationsList = Collections.emptyList();
    publishers.forEach(
        val -> {
          when(val.sendSignedAttestations(anyList()))
              .thenReturn(SafeFuture.failedFuture(new IllegalStateException("computer says no")));
          when(val.getSanitizedUrl()).thenReturn("http://host");
        });

    ignoreFuture(api.sendSignedAttestations(attestationsList));
    verify(delegate, times(1)).sendSignedAttestations(attestationsList);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(val -> verify(val, times(1)).sendSignedAttestations(attestationsList));
    verify(logger, times(2))
        .warn(anyString(), eq("attestations"), eq("http://host"), eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendAggregateAndProofs() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);
    final List<SignedAggregateAndProof> signedAggregateAndProofs = Collections.emptyList();
    publishers.forEach(
        val ->
            when(val.sendAggregateAndProofs(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendAggregateAndProofs(signedAggregateAndProofs));
    verify(delegate, times(1)).sendAggregateAndProofs(signedAggregateAndProofs);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(
        val -> verify(val, times(1)).sendAggregateAndProofs(signedAggregateAndProofs));
  }

  @Test
  void shouldLogPublisherWarnings_sendAggregateAndProofs() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers, logger);
    final List<SignedAggregateAndProof> signedAggregateAndProofs = Collections.emptyList();
    publishers.forEach(
        val -> {
          when(val.sendAggregateAndProofs(anyList()))
              .thenReturn(SafeFuture.failedFuture(new IllegalStateException("computer says no")));
          when(val.getSanitizedUrl()).thenReturn("http://host");
        });

    ignoreFuture(api.sendAggregateAndProofs(signedAggregateAndProofs));
    verify(delegate, times(1)).sendAggregateAndProofs(signedAggregateAndProofs);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(
        val -> verify(val, times(1)).sendAggregateAndProofs(signedAggregateAndProofs));
    verify(logger, times(2))
        .warn(anyString(), eq("aggregateAndProofs"), eq("http://host"), eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedBlock() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(1);
    publishers.forEach(
        val ->
            when(val.sendSignedBlock(block))
                .thenReturn(SafeFuture.completedFuture(mock(SendSignedBlockResult.class))));

    ignoreFuture(api.sendSignedBlock(block));
    verify(delegate, times(1)).sendSignedBlock(block);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(val -> verify(val, times(1)).sendSignedBlock(block));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedBlock() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers, logger);
    final SignedBeaconBlock block = data.randomSignedBeaconBlock(1);
    publishers.forEach(
        val -> {
          when(val.sendSignedBlock(block))
              .thenReturn(SafeFuture.failedFuture(new IllegalStateException("computer says no")));
          when(val.getSanitizedUrl()).thenReturn("http://host");
        });

    ignoreFuture(api.sendSignedBlock(block));
    verify(delegate, times(1)).sendSignedBlock(block);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(val -> verify(val, times(1)).sendSignedBlock(block));
    verify(logger, times(2))
        .warn(anyString(), eq("block"), eq("http://host"), eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSyncCommitteeMessages() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);
    final List<SyncCommitteeMessage> syncCommitteeMessages = Collections.emptyList();
    final SafeFuture<List<SubmitDataError>> future = new SafeFuture<>();
    when(delegate.sendSyncCommitteeMessages(any())).thenReturn(future);
    publishers.forEach(
        val ->
            when(val.sendSyncCommitteeMessages(anyList()))
                .thenReturn(SafeFuture.completedFuture(Collections.emptyList())));

    ignoreFuture(api.sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(delegate, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages);
    verifyNoMoreInteractions(delegate);
    future.complete(Collections.emptyList());

    publishers.forEach(
        val -> verify(val, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages));
  }

  @Test
  void shouldLogPublisherWarnings_sendSyncCommitteeMessages() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers, logger);
    final List<SyncCommitteeMessage> syncCommitteeMessages = Collections.emptyList();
    final SafeFuture<List<SubmitDataError>> future = new SafeFuture<>();
    when(delegate.sendSyncCommitteeMessages(any())).thenReturn(future);
    publishers.forEach(
        val -> {
          when(val.sendSyncCommitteeMessages(anyList()))
              .thenReturn(SafeFuture.failedFuture(new IllegalStateException("computer says no")));
          when(val.getSanitizedUrl()).thenReturn("http://host");
        });

    ignoreFuture(api.sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(delegate, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages);
    verifyNoMoreInteractions(delegate);
    future.complete(Collections.emptyList());
    publishers.forEach(
        val -> verify(val, times(1)).sendSyncCommitteeMessages(syncCommitteeMessages));
    verify(logger, times(2))
        .warn(anyString(), eq("syncCommitteeMessages"), eq("http://host"), eq("computer says no"));
  }

  @Test
  void shouldCallDelegateAndPublishers_sendSignedContributionAndProofs() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers);
    final List<SignedContributionAndProof> signedContributionAndProofs = Collections.emptyList();
    publishers.forEach(
        val ->
            when(val.sendSignedContributionAndProofs(anyList())).thenReturn(SafeFuture.COMPLETE));

    ignoreFuture(api.sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(delegate, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(
        val -> verify(val, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs));
  }

  @Test
  void shouldLogPublisherWarnings_sendSignedContributionAndProofs() {
    final MultiPublishingValidatorApiChannel api =
        new MultiPublishingValidatorApiChannel(delegate, publishers, logger);
    final List<SignedContributionAndProof> signedContributionAndProofs = Collections.emptyList();
    publishers.forEach(
        val -> {
          when(val.sendSignedContributionAndProofs(anyList()))
              .thenReturn(SafeFuture.failedFuture(new IllegalStateException("computer says no")));
          when(val.getSanitizedUrl()).thenReturn("http://host");
        });

    ignoreFuture(api.sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(delegate, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs);
    verifyNoMoreInteractions(delegate);

    publishers.forEach(
        val -> verify(val, times(1)).sendSignedContributionAndProofs(signedContributionAndProofs));
    verify(logger, times(2))
        .warn(
            anyString(),
            eq("signedContributionAndProofs"),
            eq("http://host"),
            eq("computer says no"));
  }
}
