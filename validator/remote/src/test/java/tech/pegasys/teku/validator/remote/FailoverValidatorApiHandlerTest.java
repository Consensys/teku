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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
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
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.SyncCommitteeDuties;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.remote.FailoverValidatorApiHandler.ValidatorApiChannelRequest;

class FailoverValidatorApiHandlerTest {

  private static final IllegalStateException EXCEPTION = new IllegalStateException("oopsy");

  private static final Spec spec = TestSpecFactory.createMinimalAltair();
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private RemoteValidatorApiChannel apiChannel1;
  private RemoteValidatorApiChannel apiChannel2;
  private RemoteValidatorApiChannel apiChannel3;

  private ValidatorLogger validatorLogger;

  private FailoverValidatorApiHandler failoverApiHandler;

  @BeforeEach
  void setUp() {
    apiChannel1 = mock(RemoteValidatorApiChannel.class);
    apiChannel2 = mock(RemoteValidatorApiChannel.class);
    apiChannel3 = mock(RemoteValidatorApiChannel.class);

    validatorLogger = mock(ValidatorLogger.class);

    final Supplier<URI> randomUriGenerator =
        () -> URI.create("http://" + dataStructureUtil.randomBytes4().toHexString() + ".com");

    when(apiChannel1.getEndpoint()).thenReturn(randomUriGenerator.get());
    when(apiChannel2.getEndpoint()).thenReturn(randomUriGenerator.get());
    when(apiChannel3.getEndpoint()).thenReturn(randomUriGenerator.get());

    failoverApiHandler =
        new FailoverValidatorApiHandler(
            List.of(apiChannel1, apiChannel2, apiChannel3), validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getFutureRequestsData")
  <T> void requestSucceedsWithoutFailover(
      final ValidatorApiChannelRequest<T> request, final T response) {

    when(request.run(apiChannel1)).thenReturn(SafeFuture.completedFuture(response));

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verifyNoInteractions(apiChannel2, apiChannel3, validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getFutureRequestsData")
  <T> void requestFailoversOnFailure(
      final ValidatorApiChannelRequest<T> request, final T response) {

    setupFailures(request, apiChannel1, apiChannel2);

    when(request.run(apiChannel3)).thenReturn(SafeFuture.completedFuture(response));

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedWithValue(response);

    verify(validatorLogger)
        .remoteBeaconNodeFailoverRequestFailed(
            apiChannel1.getEndpoint(), EXCEPTION, Optional.of(apiChannel2.getEndpoint()));
    verify(validatorLogger)
        .remoteBeaconNodeFailoverRequestFailed(
            apiChannel2.getEndpoint(), EXCEPTION, Optional.of(apiChannel3.getEndpoint()));
    verifyNoMoreInteractions(validatorLogger);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("getFutureRequestsData")
  <T> void requestFailsOnAllFailovers(final ValidatorApiChannelRequest<T> request) {

    setupFailures(request, apiChannel1, apiChannel2, apiChannel3);

    final SafeFuture<T> result = request.run(failoverApiHandler);

    assertThat(result).isCompletedExceptionally();

    verify(validatorLogger)
        .remoteBeaconNodeFailoverRequestFailed(
            apiChannel1.getEndpoint(), EXCEPTION, Optional.of(apiChannel2.getEndpoint()));
    verify(validatorLogger)
        .remoteBeaconNodeFailoverRequestFailed(
            apiChannel2.getEndpoint(), EXCEPTION, Optional.of(apiChannel3.getEndpoint()));
    verify(validatorLogger)
        .remoteBeaconNodeFailoverRequestFailed(
            apiChannel3.getEndpoint(), EXCEPTION, Optional.empty());
  }

  private <T> void setupFailures(
      final ValidatorApiChannelRequest<T> request, final RemoteValidatorApiChannel... apiChannels) {
    Stream.of(apiChannels)
        .forEach(
            apiChannel ->
                when(request.run(apiChannel)).thenReturn(SafeFuture.failedFuture(EXCEPTION)));
  }

  private static Stream<Arguments> getFutureRequestsData() {
    final GenesisData genesisData =
        new GenesisData(dataStructureUtil.randomUInt64(), dataStructureUtil.randomBytes32());
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    final IntCollection validatorIndices = IntLists.singleton(0);
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final UInt64 epoch = dataStructureUtil.randomUInt64();
    final Bytes32 randomBytes32 = dataStructureUtil.randomBytes32();
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final SubmitDataError submitDataError =
        new SubmitDataError(dataStructureUtil.randomUInt64(), "foo");
    final SignedAggregateAndProof signedAggregateAndProof =
        dataStructureUtil.randomSignedAggregateAndProof();
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil.randomSignedBlindedBeaconBlock();
    final SyncCommitteeMessage syncCommitteeMessage =
        dataStructureUtil.randomSyncCommitteeMessage();
    final SignedContributionAndProof signedContributionAndProof =
        dataStructureUtil.randomSignedContributionAndProof(slot.longValue());
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(3);

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
            Optional.of(mock(SyncCommitteeContribution.class))),
        getArguments(
            "sendSignedAttestations",
            apiChannel -> apiChannel.sendSignedAttestations(List.of(attestation)),
            List.of(submitDataError)),
        getArguments(
            "sendAggregateAndProofs",
            apiChannel -> apiChannel.sendAggregateAndProofs(List.of(signedAggregateAndProof)),
            List.of(submitDataError)),
        getArguments(
            "sendSignedBlock",
            apiChannel -> apiChannel.sendSignedBlock(signedBeaconBlock),
            mock(SendSignedBlockResult.class)),
        getArguments(
            "sendSyncCommitteeMessages",
            apiChannel -> apiChannel.sendSyncCommitteeMessages(List.of(syncCommitteeMessage)),
            List.of(submitDataError)),
        getArguments(
            "sendSignedContributionAndProofs",
            apiChannel ->
                apiChannel.sendSignedContributionAndProofs(List.of(signedContributionAndProof)),
            null),
        getArguments(
            "registerValidators",
            apiChannel -> apiChannel.registerValidators(validatorRegistrations),
            null));
  }

  private static <T> Arguments getArguments(
      final String name, final ValidatorApiChannelRequest<T> request, final T response) {
    return Arguments.of(Named.of(name, request), response);
  }
}
