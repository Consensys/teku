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

package tech.pegasys.teku.validator.remote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.ValidatorApiChannelRequest;

class FailoverValidatorApiHandlerTest {

  private static final IllegalStateException EXCEPTION = new IllegalStateException("oopsy");

  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);

  private RemoteValidatorApiChannel primaryApiChannel;
  private RemoteValidatorApiChannel failoverApiChannel1;
  private RemoteValidatorApiChannel failoverApiChannel2;

  private ValidatorLogger validatorLogger;

  private FailoverValidatorApiHandler failoverApiHandler;

  @BeforeEach
  void setUp() {
    primaryApiChannel = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel1 = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel2 = mock(RemoteValidatorApiChannel.class);

    validatorLogger = mock(ValidatorLogger.class);

    final Supplier<URI> randomUriGenerator =
        () -> URI.create("http://" + DATA_STRUCTURE_UTIL.randomBytes4().toHexString() + ".com");

    when(primaryApiChannel.getEndpoint()).thenReturn(randomUriGenerator.get());
    when(failoverApiChannel1.getEndpoint()).thenReturn(randomUriGenerator.get());
    when(failoverApiChannel2.getEndpoint()).thenReturn(randomUriGenerator.get());

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            primaryApiChannel, List.of(failoverApiChannel1, failoverApiChannel2), validatorLogger);
  }

  @Test
  void initializationFailsWhenNoFailoversAreDefined() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            failoverApiHandler =
                new FailoverValidatorApiHandler(primaryApiChannel, List.of(), validatorLogger));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestSucceedsWithoutFailover(
      final ValidatorApiChannelRequest<T> request, final T response) {

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2, validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailoversOnFailure(
      final ValidatorApiChannelRequest<T> request, final T response) {

    setupSuccesses(request, response, failoverApiChannel2);
    setupFailures(request, primaryApiChannel, failoverApiChannel1);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyNoInteractions(validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailsOnAllFailovers(final ValidatorApiChannelRequest<T> request) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedExceptionally();

    verify(validatorLogger).remoteBeaconNodeRequestFailedOnAllConfiguredEndpoints();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final T response) {

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyNoInteractions(validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllNodesButFailsOnOneFailover(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final T response) {

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1);
    setupFailures(request, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyNoInteractions(validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeButRelayedToFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final T response) {

    setupFailures(request, primaryApiChannel);
    setupSuccesses(request, response, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyNoInteractions(validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeAndAllFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedExceptionally();
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verify(validatorLogger).remoteBeaconNodeRequestFailedOnAllConfiguredEndpoints();
  }

  private <T> void setupSuccesses(
      final ValidatorApiChannelRequest<T> request,
      final T response,
      final RemoteValidatorApiChannel... apiChannels) {
    Stream.of(apiChannels)
        .forEach(
            apiChannel ->
                when(request.run(apiChannel)).thenReturn(SafeFuture.completedFuture(response)));
  }

  private <T> void setupFailures(
      final ValidatorApiChannelRequest<T> request, final RemoteValidatorApiChannel... apiChannels) {
    Stream.of(apiChannels)
        .forEach(
            apiChannel ->
                when(request.run(apiChannel)).thenReturn(SafeFuture.failedFuture(EXCEPTION)));
  }

  private static Stream<Arguments> getRequestsUsingFailover() {
    final GenesisData genesisData =
        new GenesisData(DATA_STRUCTURE_UTIL.randomUInt64(), DATA_STRUCTURE_UTIL.randomBytes32());
    final BLSPublicKey publicKey = DATA_STRUCTURE_UTIL.randomPublicKey();
    final IntCollection validatorIndices = IntLists.singleton(0);
    final UInt64 slot = DATA_STRUCTURE_UTIL.randomUInt64();
    final BLSSignature randaoReveal = DATA_STRUCTURE_UTIL.randomSignature();
    final UInt64 epoch = DATA_STRUCTURE_UTIL.randomUInt64();
    final Bytes32 randomBytes32 = DATA_STRUCTURE_UTIL.randomBytes32();
    final Attestation attestation = DATA_STRUCTURE_UTIL.randomAttestation();

    return Stream.of(
        getArguments(
            "getGenesisData", ValidatorApiChannel::getGenesisData, Optional.of(genesisData)),
        getArguments(
            "getValidatorIndices",
            apiChannel -> apiChannel.getValidatorIndices(List.of(publicKey)),
            Map.of(publicKey, 0)),
        getArguments(
            "getValidatorStatuses",
            apiChannel -> apiChannel.getValidatorStatuses(List.of(publicKey)),
            Optional.of(Map.of(publicKey, ValidatorStatus.active_ongoing))),
        getArguments(
            "getAttestationDuties",
            apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices),
            Optional.of(mock(AttesterDuties.class))),
        getArguments(
            "getSyncCommitteeDuties",
            apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices),
            Optional.of(mock(SyncCommitteeDuties.class))),
        getArguments(
            "getProposerDuties",
            apiChannel -> apiChannel.getProposerDuties(epoch),
            Optional.of(mock(ProposerDuties.class))),
        getArguments(
            "createUnsignedBlock",
            apiChannel ->
                apiChannel.createUnsignedBlock(slot, randaoReveal, Optional.empty(), true),
            Optional.of(mock(BeaconBlock.class))),
        getArguments(
            "createAttestationData",
            apiChannel -> apiChannel.createAttestationData(slot, 0),
            Optional.of(mock(AttestationData.class))),
        getArguments(
            "createAggregate",
            apiChannel -> apiChannel.createAggregate(slot, randomBytes32),
            Optional.of(attestation)),
        getArguments(
            "createSyncCommitteeContribution",
            apiChannel -> apiChannel.createSyncCommitteeContribution(slot, 0, randomBytes32),
            Optional.of(mock(SyncCommitteeContribution.class))));
  }

  private static Stream<Arguments> getRelayRequests() {
    final CommitteeSubscriptionRequest committeeSubscriptionRequest =
        new CommitteeSubscriptionRequest(0, 0, UInt64.ZERO, UInt64.ONE, true);
    final SyncCommitteeSubnetSubscription syncCommitteeSubnetSubscription =
        new SyncCommitteeSubnetSubscription(0, IntSet.of(1), UInt64.ZERO);
    final SubnetSubscription subnetSubscription = new SubnetSubscription(0, UInt64.ONE);
    final Attestation attestation = DATA_STRUCTURE_UTIL.randomAttestation();
    final SubmitDataError submitDataError =
        new SubmitDataError(DATA_STRUCTURE_UTIL.randomUInt64(), "foo");
    final SignedAggregateAndProof signedAggregateAndProof =
        DATA_STRUCTURE_UTIL.randomSignedAggregateAndProof();
    final SignedBeaconBlock signedBeaconBlock =
        DATA_STRUCTURE_UTIL.randomSignedBlindedBeaconBlock();
    final SyncCommitteeMessage syncCommitteeMessage =
        DATA_STRUCTURE_UTIL.randomSyncCommitteeMessage();
    final SignedContributionAndProof signedContributionAndProof =
        DATA_STRUCTURE_UTIL.randomSignedContributionAndProof(2);
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        DATA_STRUCTURE_UTIL.randomSignedValidatorRegistrations(3);

    return Stream.of(
        getArguments(
            "subscribeToBeaconCommittee",
            apiChannel ->
                apiChannel.subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            null),
        getArguments(
            "subscribeToSyncCommitteeSubnets",
            apiChannel ->
                apiChannel.subscribeToSyncCommitteeSubnets(
                    List.of(syncCommitteeSubnetSubscription)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToSyncCommitteeSubnets(List.of(syncCommitteeSubnetSubscription)),
            null),
        getArguments(
            "subscribeToPersistentSubnets",
            apiChannel -> apiChannel.subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            apiChannel ->
                verify(apiChannel).subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            null),
        getArguments(
            "sendSignedAttestations",
            apiChannel -> apiChannel.sendSignedAttestations(List.of(attestation)),
            apiChannel -> verify(apiChannel).sendSignedAttestations(List.of(attestation)),
            List.of(submitDataError)),
        getArguments(
            "sendAggregateAndProofs",
            apiChannel -> apiChannel.sendAggregateAndProofs(List.of(signedAggregateAndProof)),
            apiChannel ->
                verify(apiChannel).sendAggregateAndProofs(List.of(signedAggregateAndProof)),
            List.of(submitDataError)),
        getArguments(
            "sendSignedBlock",
            apiChannel -> apiChannel.sendSignedBlock(signedBeaconBlock),
            apiChannel -> verify(apiChannel).sendSignedBlock(signedBeaconBlock),
            mock(SendSignedBlockResult.class)),
        getArguments(
            "sendSyncCommitteeMessages",
            apiChannel -> apiChannel.sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
            apiChannel ->
                verify(apiChannel).sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
            List.of(submitDataError)),
        getArguments(
            "sendSignedContributionAndProofs",
            apiChannel ->
                apiChannel.sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
            apiChannel ->
                verify(apiChannel)
                    .sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
            null),
        getArguments(
            "registerValidators",
            apiChannel -> apiChannel.registerValidators(validatorRegistrations),
            apiChannel -> verify(apiChannel).registerValidators(validatorRegistrations),
            null));
  }

  private static <T> Arguments getArguments(
      final String name, final ValidatorApiChannelRequest<T> request, final T response) {
    return Arguments.of(Named.of(name, request), response);
  }

  private static <T> Arguments getArguments(
      final String name,
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final T response) {
    return Arguments.of(Named.of(name, request), verifyCallIsMade, response);
  }
}
