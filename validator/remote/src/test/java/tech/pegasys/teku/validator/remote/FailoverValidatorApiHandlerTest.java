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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import org.apache.tuweni.bytes.Bytes32;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
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
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.SyncCommitteeSubnetSubscription;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.metrics.BeaconNodeRequestLabels;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.RequestOutcome;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.ValidatorApiChannelRequest;

class FailoverValidatorApiHandlerTest {

  private static final Spec SPEC = TestSpecFactory.createMinimalAltair();
  private static final DataStructureUtil DATA_STRUCTURE_UTIL = new DataStructureUtil(SPEC);

  private final StubMetricsSystem stubMetricsSystem = new StubMetricsSystem();

  private RemoteValidatorApiChannel primaryApiChannel;
  private RemoteValidatorApiChannel failoverApiChannel1;
  private RemoteValidatorApiChannel failoverApiChannel2;
  private FailoverValidatorApiHandler failoverApiHandler;

  @BeforeEach
  void setUp() {
    primaryApiChannel = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel1 = mock(RemoteValidatorApiChannel.class);
    failoverApiChannel2 = mock(RemoteValidatorApiChannel.class);

    final Supplier<HttpUrl> randomHttpUrlGenerator =
        () -> HttpUrl.get("http://" + DATA_STRUCTURE_UTIL.randomBytes4().toHexString() + ".com");

    when(primaryApiChannel.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());
    when(failoverApiChannel1.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());
    when(failoverApiChannel2.getEndpoint()).thenReturn(randomHttpUrlGenerator.get());

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            primaryApiChannel,
            List.of(failoverApiChannel1, failoverApiChannel2),
            true,
            stubMetricsSystem);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestSucceedsWithoutFailover(
      final ValidatorApiChannelRequest<T> request, final String methodLabel, final T response) {

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailoversOnFailure(
      final ValidatorApiChannelRequest<T> request, final String methodLabel, final T response) {

    setupSuccesses(request, response, failoverApiChannel2);
    setupFailures(request, primaryApiChannel, failoverApiChannel1);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailsOnAllFailovers(
      final ValidatorApiChannelRequest<T> request, final String methodLabel) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    verifyFailoverRequestExceptionIsThrown(result, methodLabel);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRequestsUsingFailover")
  <T> void requestFailsAndNoFailoversConfigured(final ValidatorApiChannelRequest<T> request) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(primaryApiChannel, List.of(), true, stubMetricsSystem);

    setupFailures(request, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedExceptionally();

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getSubscriptionRequests")
  <T> void requestIsNotRelayedToFailoversIfFailoversSendSubnetSubscriptionsIsDisabled(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            primaryApiChannel,
            List.of(failoverApiChannel1, failoverApiChannel2),
            false,
            stubMetricsSystem);

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsNotRelayedIfNoFailoversAreConfigured(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    failoverApiHandler =
        new FailoverValidatorApiHandler(primaryApiChannel, List.of(), true, stubMetricsSystem);

    setupSuccesses(request, response, primaryApiChannel);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));

    verifyNoInteractions(failoverApiChannel1, failoverApiChannel2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestIsRelayedToAllNodesButFailsOnOneFailover(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupSuccesses(request, response, primaryApiChannel, failoverApiChannel1);
    setupFailures(request, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeButRelayedToFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupFailures(request, primaryApiChannel);
    setupSuccesses(request, response, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeAndOneFailoverDoesNotRespond(
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {

    setupFailures(request, primaryApiChannel);
    SafeFuture<T> neverCompletedFuture = new SafeFuture<>();
    when(request.run(failoverApiChannel1)).thenReturn(neverCompletedFuture);
    setupSuccesses(request, response, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);
    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 0L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 1L, RequestOutcome.ERROR, 0L));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getRelayRequests")
  <T> void requestFailsOnPrimaryNodeAndAllFailoverNodes(
      final ValidatorApiChannelRequest<T> request,
      @NotNull final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel) {

    setupFailures(request, primaryApiChannel, failoverApiChannel1, failoverApiChannel2);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    verifyFailoverRequestExceptionIsThrown(result, methodLabel);

    verifyCallIsMade.accept(primaryApiChannel);

    verifyCallIsMade.accept(failoverApiChannel1);
    verifyCallIsMade.accept(failoverApiChannel2);

    verifyRequestCounters(
        primaryApiChannel,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel1,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
    verifyRequestCounters(
        failoverApiChannel2,
        methodLabel,
        Map.of(RequestOutcome.SUCCESS, 0L, RequestOutcome.ERROR, 1L));
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
            apiChannel -> {
              final IllegalStateException exception =
                  new IllegalStateException(
                      String.format("Request failed for %s", apiChannel.getEndpoint()));
              when(request.run(apiChannel)).thenReturn(SafeFuture.failedFuture(exception));
            });
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
            "getGenesisData",
            ValidatorApiChannel::getGenesisData,
            BeaconNodeRequestLabels.GET_GENESIS_METHOD,
            Optional.of(genesisData)),
        getArguments(
            "getValidatorIndices",
            apiChannel -> apiChannel.getValidatorIndices(List.of(publicKey)),
            BeaconNodeRequestLabels.GET_VALIDATOR_INDICES_METHOD,
            Map.of(publicKey, 0)),
        getArguments(
            "getValidatorStatuses",
            apiChannel -> apiChannel.getValidatorStatuses(List.of(publicKey)),
            BeaconNodeRequestLabels.GET_VALIDATOR_STATUSES_METHOD,
            Optional.of(Map.of(publicKey, ValidatorStatus.active_ongoing))),
        getArguments(
            "getAttestationDuties",
            apiChannel -> apiChannel.getAttestationDuties(epoch, validatorIndices),
            BeaconNodeRequestLabels.GET_ATTESTATION_DUTIES_METHOD,
            Optional.of(mock(AttesterDuties.class))),
        getArguments(
            "getSyncCommitteeDuties",
            apiChannel -> apiChannel.getSyncCommitteeDuties(epoch, validatorIndices),
            BeaconNodeRequestLabels.GET_SYNC_COMMITTEE_DUTIES_METHOD,
            Optional.of(mock(SyncCommitteeDuties.class))),
        getArguments(
            "getProposerDuties",
            apiChannel -> apiChannel.getProposerDuties(epoch),
            BeaconNodeRequestLabels.GET_PROPOSER_DUTIES_REQUESTS_METHOD,
            Optional.of(mock(ProposerDuties.class))),
        getArguments(
            "createUnsignedBlock",
            apiChannel ->
                apiChannel.createUnsignedBlock(slot, randaoReveal, Optional.empty(), true),
            BeaconNodeRequestLabels.CREATE_UNSIGNED_BLOCK_METHOD,
            Optional.of(mock(BeaconBlock.class))),
        getArguments(
            "createAttestationData",
            apiChannel -> apiChannel.createAttestationData(slot, 0),
            BeaconNodeRequestLabels.CREATE_ATTESTATION_METHOD,
            Optional.of(mock(AttestationData.class))),
        getArguments(
            "createAggregate",
            apiChannel -> apiChannel.createAggregate(slot, randomBytes32),
            BeaconNodeRequestLabels.CREATE_AGGREGATE_METHOD,
            Optional.of(attestation)),
        getArguments(
            "createSyncCommitteeContribution",
            apiChannel -> apiChannel.createSyncCommitteeContribution(slot, 0, randomBytes32),
            BeaconNodeRequestLabels.CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD,
            Optional.of(mock(SyncCommitteeContribution.class))));
  }

  private static Stream<Arguments> getRelayRequests() {
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

    return Streams.concat(
        getSubscriptionRequests(),
        Stream.of(
            getArguments(
                "sendSignedAttestations",
                apiChannel -> apiChannel.sendSignedAttestations(List.of(attestation)),
                apiChannel -> verify(apiChannel).sendSignedAttestations(List.of(attestation)),
                BeaconNodeRequestLabels.PUBLISH_ATTESTATION_METHOD,
                List.of(submitDataError)),
            getArguments(
                "sendAggregateAndProofs",
                apiChannel -> apiChannel.sendAggregateAndProofs(List.of(signedAggregateAndProof)),
                apiChannel ->
                    verify(apiChannel).sendAggregateAndProofs(List.of(signedAggregateAndProof)),
                BeaconNodeRequestLabels.PUBLISH_AGGREGATE_AND_PROOFS_METHOD,
                List.of(submitDataError)),
            getArguments(
                "sendSignedBlock",
                apiChannel -> apiChannel.sendSignedBlock(signedBeaconBlock),
                apiChannel -> verify(apiChannel).sendSignedBlock(signedBeaconBlock),
                BeaconNodeRequestLabels.PUBLISH_BLOCK_METHOD,
                mock(SendSignedBlockResult.class)),
            getArguments(
                "sendSyncCommitteeMessages",
                apiChannel -> apiChannel.sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
                apiChannel ->
                    verify(apiChannel).sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
                BeaconNodeRequestLabels.SEND_SYNC_COMMITTEE_MESSAGES_METHOD,
                List.of(submitDataError)),
            getArguments(
                "sendSignedContributionAndProofs",
                apiChannel ->
                    apiChannel.sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
                apiChannel ->
                    verify(apiChannel)
                        .sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
                BeaconNodeRequestLabels.SEND_CONTRIBUTIONS_AND_PROOFS_METHOD,
                null),
            getArguments(
                "registerValidators",
                apiChannel -> apiChannel.registerValidators(validatorRegistrations),
                apiChannel -> verify(apiChannel).registerValidators(validatorRegistrations),
                BeaconNodeRequestLabels.REGISTER_VALIDATORS_METHOD,
                null)));
  }

  private static Stream<Arguments> getSubscriptionRequests() {
    final CommitteeSubscriptionRequest committeeSubscriptionRequest =
        new CommitteeSubscriptionRequest(0, 0, UInt64.ZERO, UInt64.ONE, true);
    final SyncCommitteeSubnetSubscription syncCommitteeSubnetSubscription =
        new SyncCommitteeSubnetSubscription(0, IntSet.of(1), UInt64.ZERO);
    final SubnetSubscription subnetSubscription = new SubnetSubscription(0, UInt64.ONE);

    return Stream.of(
        getArguments(
            "subscribeToBeaconCommittee",
            apiChannel ->
                apiChannel.subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToBeaconCommittee(List.of(committeeSubscriptionRequest)),
            BeaconNodeRequestLabels.BEACON_COMMITTEE_SUBSCRIPTION_METHOD,
            null),
        getArguments(
            "subscribeToSyncCommitteeSubnets",
            apiChannel ->
                apiChannel.subscribeToSyncCommitteeSubnets(
                    List.of(syncCommitteeSubnetSubscription)),
            apiChannel ->
                verify(apiChannel)
                    .subscribeToSyncCommitteeSubnets(List.of(syncCommitteeSubnetSubscription)),
            BeaconNodeRequestLabels.SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD,
            null),
        getArguments(
            "subscribeToPersistentSubnets",
            apiChannel -> apiChannel.subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            apiChannel ->
                verify(apiChannel).subscribeToPersistentSubnets(Set.of(subnetSubscription)),
            BeaconNodeRequestLabels.PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD,
            null));
  }

  private static <T> Arguments getArguments(
      final String name,
      final ValidatorApiChannelRequest<T> request,
      final String methodLabel,
      final T response) {
    return Arguments.of(Named.of(name, request), methodLabel, response);
  }

  private static <T> Arguments getArguments(
      final String name,
      final ValidatorApiChannelRequest<T> request,
      final Consumer<ValidatorApiChannel> verifyCallIsMade,
      final String methodLabel,
      final T response) {
    return Arguments.of(Named.of(name, request), verifyCallIsMade, methodLabel, response);
  }

  private void verifyRequestCounters(
      final RemoteValidatorApiChannel failoverApiChannel,
      final String methodLabel,
      final Map<RequestOutcome, Long> expectedCountByRequestOutcome) {
    expectedCountByRequestOutcome.forEach(
        (key, value) ->
            assertThat(getFailoverCounterValue(failoverApiChannel, methodLabel, key))
                .isEqualTo(value));
  }

  private <T> void verifyFailoverRequestExceptionIsThrown(
      final SafeFuture<T> result, final String methodLabel) {
    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(FailoverRequestException.class)
        .message()
        .satisfies(
            message -> {
              assertThat(message)
                  .contains(
                      String.format(
                          "Remote request (%s) failed on all configured Beacon Node endpoints",
                          methodLabel));
              assertThat(message)
                  .contains(
                      getExceptionMessageForEndpoint(primaryApiChannel.getEndpoint()),
                      getExceptionMessageForEndpoint(failoverApiChannel1.getEndpoint()),
                      getExceptionMessageForEndpoint(failoverApiChannel2.getEndpoint()));
            });
  }

  private String getExceptionMessageForEndpoint(final HttpUrl endpoint) {
    return String.format(
        "%s: java.lang.IllegalStateException: Request failed for %s", endpoint, endpoint);
  }

  private long getFailoverCounterValue(
      final RemoteValidatorApiChannel apiChannel,
      final String methodLabel,
      final RequestOutcome outcome) {
    return stubMetricsSystem
        .getCounter(
            TekuMetricCategory.VALIDATOR,
            FailoverValidatorApiHandler.FAILOVER_BEACON_NODES_REQUESTS_COUNTER_NAME)
        .getValue(apiChannel.getEndpoint().toString(), methodLabel, outcome.toString());
  }
}
