/*
 * Copyright Consensys Software Inc., 2022
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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_PUBLIC_KEY_BATCH_SIZE;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_RATE_LIMITING_RETRIES;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import okhttp3.HttpUrl;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailure;
import tech.pegasys.teku.api.response.v1.beacon.PostDataFailureResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorStatus;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.response.v1.validator.ValidatorLiveness;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.Validator;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorRestApiClient;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

class RemoteValidatorApiHandlerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final HttpUrl endpoint = HttpUrl.get("http://localhost:5051");
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ValidatorRestApiClient apiClient = mock(ValidatorRestApiClient.class);

  private final OkHttpValidatorTypeDefClient typeDefClient =
      mock(OkHttpValidatorTypeDefClient.class);

  private RemoteValidatorApiHandler apiHandler;

  @BeforeEach
  public void beforeEach() {
    apiHandler =
        new RemoteValidatorApiHandler(endpoint, spec, apiClient, typeDefClient, asyncRunner, true);
  }

  @Test
  public void beaconCommitteeSelectionsRequest_ReturnBeaconCommitteeSelectionProof() {
    final String blsSignatureHex =
        dataStructureUtil.randomSignature().toBytesCompressed().toHexString();
    final BeaconCommitteeSelectionProof proof =
        new BeaconCommitteeSelectionProof.Builder()
            .validatorIndex(1)
            .slot(ONE)
            .selectionProof(blsSignatureHex)
            .build();

    when(typeDefClient.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(Optional.of(List.of(proof)));

    final SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> future =
        apiHandler.getBeaconCommitteeSelectionProof(List.of(proof));
    asyncRunner.executeQueuedActions();

    final List<BeaconCommitteeSelectionProof> response = unwrapToValue(future);
    final BeaconCommitteeSelectionProof responseProof = response.get(0);
    assertThat(responseProof.getValidatorIndex()).isEqualTo(proof.getValidatorIndex());
    assertThat(responseProof.getSlot()).isEqualTo(proof.getSlot());
    assertThat(responseProof.getSelectionProof()).isEqualTo(proof.getSelectionProof());
  }

  @Test
  public void syncCommitteeSelectionsRequest_ReturnSyncCommitteeSelectionProof() {
    final String blsSignatureHex =
        dataStructureUtil.randomSignature().toBytesCompressed().toHexString();
    final SyncCommitteeSelectionProof proof =
        new SyncCommitteeSelectionProof.Builder()
            .validatorIndex(1)
            .slot(ONE)
            .subcommitteeIndex(2)
            .selectionProof(blsSignatureHex)
            .build();

    when(typeDefClient.getSyncCommitteeSelectionProof(any()))
        .thenReturn(Optional.of(List.of(proof)));

    final SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> future =
        apiHandler.getSyncCommitteeSelectionProof(List.of(proof));
    asyncRunner.executeQueuedActions();

    final List<SyncCommitteeSelectionProof> response = unwrapToValue(future);
    final SyncCommitteeSelectionProof responseProof = response.get(0);
    assertThat(responseProof.getValidatorIndex()).isEqualTo(proof.getValidatorIndex());
    assertThat(responseProof.getSlot()).isEqualTo(proof.getSlot());
    assertThat(responseProof.getSubcommitteeIndex()).isEqualTo(proof.getSubcommitteeIndex());
    assertThat(responseProof.getSelectionProof()).isEqualTo(proof.getSelectionProof());
  }

  @Test
  public void getsEndpoint() {
    assertThat(apiHandler.getEndpoint()).isEqualTo(endpoint);
  }

  @Test
  public void getSyncingStatus_ReturnsSyncingStatus() {
    final SyncingStatus syncingStatus =
        new SyncingStatus(ONE, ONE, true, Optional.of(true), Optional.of(true));
    when(typeDefClient.getSyncingStatus()).thenReturn(syncingStatus);

    final SafeFuture<SyncingStatus> future = apiHandler.getSyncingStatus();

    asyncRunner.executeQueuedActions();

    assertThat(future).isCompletedWithValue(syncingStatus);
  }

  @Test
  public void getGenesisTime_WhenPresent_ReturnsValue() {
    final UInt64 genesisTime = dataStructureUtil.randomUInt64();
    when(typeDefClient.getGenesis())
        .thenReturn(Optional.of(new GenesisData(genesisTime, dataStructureUtil.randomBytes32())));

    SafeFuture<Optional<GenesisData>> future = apiHandler.getGenesisData();

    assertThat(unwrapToValue(future).getGenesisTime()).isEqualTo(genesisTime);
  }

  @Test
  public void getGenesisTime_WhenNotPresent_ReturnsEmpty() {
    when(typeDefClient.getGenesis()).thenReturn(Optional.empty());

    SafeFuture<Optional<GenesisData>> future = apiHandler.getGenesisData();

    assertThat(unwrapToOptional(future)).isNotPresent();
  }

  @Test
  void getValidatorIndices_WithEmptyPublicKeys_ReturnsEmptyMap() {
    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(emptyList());

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).isEmpty();
  }

  @Test
  void getValidatorIndices_MakesSingleRequestUsingPost() {
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());
    when(apiClient.postValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(validatorResponse(1, key1), validatorResponse(2, key2))));

    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    verify(apiClient).postValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_DoesNotAttemptPostAgainIfNotExisting() {
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());

    // simulate POST not existing
    when(apiClient.postValidators(any())).thenThrow(PostStateValidatorsNotExistingException.class);
    when(typeDefClient.getStateValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(stateValidatorData(1, key1), stateValidatorData(2, key2))));

    // call method twice
    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));
    final SafeFuture<Map<BLSPublicKey, Integer>> future1 =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(future1).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    assertThat(safeJoin(future1)).containsOnly(entry(key1, 1), entry(key2, 2));
    // POST only called once
    verify(apiClient, times(1)).postValidators(expectedValidatorIds);
    // GET called twice
    verify(typeDefClient, times(2)).getStateValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_WithSmallNumberOfPublicKeys_RequestsSingleBatch() {
    // simulate POST not existing
    when(apiClient.postValidators(any())).thenThrow(PostStateValidatorsNotExistingException.class);
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());
    when(typeDefClient.getStateValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(stateValidatorData(1, key1), stateValidatorData(2, key2))));

    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    verify(typeDefClient).getStateValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_WithLargeNumberOfPublicKeys_CombinesMultipleBatches() {
    // simulate POST not existing
    when(apiClient.postValidators(any())).thenThrow(PostStateValidatorsNotExistingException.class);
    // Need to ensure the URL length limit isn't exceeded, so send requests in batches
    final List<BLSPublicKey> allKeys =
        IntStream.range(0, MAX_PUBLIC_KEY_BATCH_SIZE * 3 - 2)
            .mapToObj(index -> dataStructureUtil.randomPublicKey())
            .collect(toList());

    final List<String> allSerializedKeys =
        allKeys.stream().map(key -> key.toBytesCompressed().toHexString()).collect(toList());

    final List<String> expectedBatch1 = allSerializedKeys.subList(0, MAX_PUBLIC_KEY_BATCH_SIZE);
    final List<String> expectedBatch2 =
        allSerializedKeys.subList(MAX_PUBLIC_KEY_BATCH_SIZE, MAX_PUBLIC_KEY_BATCH_SIZE * 2);
    final List<String> expectedBatch3 =
        allSerializedKeys.subList(MAX_PUBLIC_KEY_BATCH_SIZE * 2, allKeys.size());

    final List<StateValidatorData> batch1Data =
        List.of(stateValidatorData(10, allKeys.get(0)), stateValidatorData(11, allKeys.get(3)));
    final List<StateValidatorData> batch2Data =
        List.of(
            stateValidatorData(20, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE)),
            stateValidatorData(21, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3)));
    final List<StateValidatorData> batch3Data =
        List.of(
            stateValidatorData(30, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2)),
            stateValidatorData(31, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3)));

    when(typeDefClient.getStateValidators(expectedBatch1)).thenReturn(Optional.of(batch1Data));
    when(typeDefClient.getStateValidators(expectedBatch2)).thenReturn(Optional.of(batch2Data));
    when(typeDefClient.getStateValidators(expectedBatch3)).thenReturn(Optional.of(batch3Data));

    final SafeFuture<Map<BLSPublicKey, Integer>> future = apiHandler.getValidatorIndices(allKeys);

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future))
        .containsOnly(
            entry(allKeys.get(0), 10),
            entry(allKeys.get(3), 11),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE), 20),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3), 21),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2), 30),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3), 31));
    verify(typeDefClient).getStateValidators(expectedBatch1);
    verify(typeDefClient).getStateValidators(expectedBatch2);
    verify(typeDefClient).getStateValidators(expectedBatch3);
  }

  @Test
  public void getAttestationDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of());

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getAttestationDuties_WhenNoneFound_ReturnsEmpty() {
    when(typeDefClient.postAttesterDuties(any(), any()))
        .thenReturn(
            Optional.of(
                new AttesterDuties(
                    false, dataStructureUtil.randomBytes32(), Collections.emptyList())));

    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of(1234));

    assertThat(unwrapToValue(future).getDuties()).isEmpty();
  }

  @Test
  public void getAttestationDuties_WhenFound_ReturnsDuties() {
    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final int validatorIndex = 472;
    final int committeeLength = 2;
    final int committeeIndex = 1;
    final int validatorCommitteeIndex = 3;
    final int committeesAtSlot = 15;
    final AttesterDuty expectedValidatorDuties =
        new AttesterDuty(
            blsPublicKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            validatorCommitteeIndex,
            UInt64.ZERO);

    when(typeDefClient.postAttesterDuties(ONE, IntList.of(validatorIndex)))
        .thenReturn(
            Optional.of(
                new AttesterDuties(
                    false, dataStructureUtil.randomBytes32(), List.of(expectedValidatorDuties))));

    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of(validatorIndex));

    AttesterDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0)).isEqualTo(expectedValidatorDuties);
  }

  @Test
  public void getProposerDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenNoneFound_ReturnsEmpty() {
    when(typeDefClient.getProposerDuties(any()))
        .thenReturn(
            Optional.of(
                new ProposerDuties(
                    Bytes32.fromHexString("0x1234"), Collections.emptyList(), false)));

    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE);

    assertThat(unwrapToValue(future).getDuties()).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenFound_ReturnsDuties() {
    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final int validatorIndex = 472;
    final ProposerDuty schemaValidatorDuties =
        new ProposerDuty(blsPublicKey, validatorIndex, UInt64.ZERO);
    final ProposerDuty expectedValidatorDuties =
        new ProposerDuty(blsPublicKey, validatorIndex, UInt64.ZERO);
    final ProposerDuties response =
        new ProposerDuties(Bytes32.fromHexString("0x1234"), List.of(schemaValidatorDuties), false);

    when(typeDefClient.getProposerDuties(ONE)).thenReturn(Optional.of(response));

    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE);

    ProposerDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0)).isEqualTo(expectedValidatorDuties);
    assertThat(validatorDuties.getDependentRoot()).isEqualTo(response.getDependentRoot());
  }

  @Test
  public void createAttestationData_WhenNone_ReturnsEmpty() {
    when(typeDefClient.createAttestationData(any(), anyInt())).thenReturn(Optional.empty());

    SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(ONE, 0);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createAttestationData_WhenFound_ReturnsAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();

    when(typeDefClient.createAttestationData(ONE, 0))
        .thenReturn(Optional.of(attestation.getData()));

    SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(ONE, 0);

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(attestation.getData());
  }

  @Test
  public void sendSignedAttestation_InvokeApiWithCorrectRequest() {
    final Attestation attestation = dataStructureUtil.randomAttestation();

    final PostDataFailureResponse failureResponse =
        new PostDataFailureResponse(
            SC_BAD_REQUEST, "Oh no", List.of(new PostDataFailure(UInt64.ZERO, "Bad")));
    when(apiClient.sendSignedAttestations(any())).thenReturn(Optional.of(failureResponse));

    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<tech.pegasys.teku.api.schema.Attestation>> argumentCaptor =
        ArgumentCaptor.forClass(List.class);

    final SafeFuture<List<SubmitDataError>> result =
        apiHandler.sendSignedAttestations(List.of(attestation));
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendSignedAttestations(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(List.of(schemaAttestation));
    assertThat(result).isCompletedWithValue(List.of(new SubmitDataError(UInt64.ZERO, "Bad")));
  }

  @Test
  public void createUnsignedBlock_WhenNoneFound_ReturnsEmpty() {
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();

    SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(
            ONE, blsSignature, Optional.of(Bytes32.random()), Optional.of(false), Optional.empty());

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenFound_ReturnsBlock() {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    when(typeDefClient.createUnsignedBlock(
            eq(blockContainerAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(false)))
        .thenReturn(Optional.of(blockContainerAndMetaData));

    SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(
            ONE, blsSignature, graffiti, Optional.of(false), Optional.empty());

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContainerAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContainerAndMetaData.blockContainer());
  }

  @Test
  public void createUnsignedBlock_viaBlockV3_WhenFound_ReturnsBlock() {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    // we expect new block API to be called (with proposer boost factor parameter instead of blinded
    // parameter)
    when(typeDefClient.createUnsignedBlock(
            eq(blockContainerAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(Optional.of(ONE))))
        .thenReturn(Optional.of(blockContainerAndMetaData));

    SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(
            ONE, blsSignature, graffiti, Optional.empty(), Optional.of(ONE));

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContainerAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContainerAndMetaData.blockContainer());
  }

  @Test
  public void createUnsignedBlock_WhenFound_ReturnsBlockContents() {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil denebDataStructureUtil = new DataStructureUtil(denebSpec);
    final BlockContainerAndMetaData blockContentsAndMetaData =
        denebDataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = denebDataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    when(typeDefClient.createUnsignedBlock(
            eq(blockContentsAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(false)))
        .thenReturn(Optional.of(blockContentsAndMetaData));

    SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(
            ONE, blsSignature, graffiti, Optional.of(false), Optional.empty());

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContentsAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContentsAndMetaData.blockContainer());
  }

  @Test
  public void sendSignedBlock_InvokeApiWithCorrectRequest() {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.signedBlock(beaconBlock, signature);
    final SendSignedBlockResult expectedResult = SendSignedBlockResult.success(Bytes32.ZERO);

    when(typeDefClient.sendSignedBlock(any())).thenReturn(expectedResult);

    ArgumentCaptor<SignedBeaconBlock> argumentCaptor =
        ArgumentCaptor.forClass(SignedBeaconBlock.class);

    final SafeFuture<SendSignedBlockResult> result =
        apiHandler.sendSignedBlock(signedBeaconBlock, BroadcastValidationLevel.NOT_REQUIRED);
    asyncRunner.executeQueuedActions();

    verify(typeDefClient).sendSignedBlock(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).isEqualTo(signedBeaconBlock);
    assertThat(result).isCompletedWithValue(expectedResult);
  }

  @Test
  public void createAggregate_WhenNotFound_ReturnsEmpty() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 attHashTreeRoot = Bytes32.random();

    when(apiClient.createAggregate(eq(slot), eq(attHashTreeRoot))).thenReturn(Optional.empty());

    SafeFuture<Optional<Attestation>> future = apiHandler.createAggregate(slot, attHashTreeRoot);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createAggregate_WhenFound_ReturnsAttestation() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 attHashTreeRoot = Bytes32.random();

    final Attestation attestation = dataStructureUtil.randomAttestation();
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    when(apiClient.createAggregate(eq(slot), eq(attHashTreeRoot)))
        .thenReturn(Optional.of(schemaAttestation));

    SafeFuture<Optional<Attestation>> future = apiHandler.createAggregate(slot, attHashTreeRoot);

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(attestation);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void sendsAggregateAndProof_InvokeApiWithCorrectRequest() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SignedAggregateAndProof signedAggregateAndProof =
        spec.getGenesisSchemaDefinitions()
            .getSignedAggregateAndProofSchema()
            .create(aggregateAndProof, signature);

    tech.pegasys.teku.api.schema.SignedAggregateAndProof schemaSignedAggAndProof =
        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(signedAggregateAndProof);

    ArgumentCaptor<List<tech.pegasys.teku.api.schema.SignedAggregateAndProof>> argumentCaptor =
        ArgumentCaptor.forClass(List.class);

    final SafeFuture<List<SubmitDataError>> result =
        apiHandler.sendAggregateAndProofs(List.of(signedAggregateAndProof));
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendAggregateAndProofs(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(List.of(schemaSignedAggAndProof));
    assertThat(result).isCompletedWithValue(emptyList());
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_InvokeApi() {
    final int validatorIndex = 3;
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = ONE;
    final boolean isAggregator = true;
    final UInt64 committeesAtSlot = UInt64.valueOf(23);
    final List<CommitteeSubscriptionRequest> requests =
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, isAggregator));

    final SafeFuture<Void> result = apiHandler.subscribeToBeaconCommittee(requests);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(apiClient).subscribeToBeaconCommittee(requests);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void subscribeToPersistentSubnets_InvokeApi() {
    final int subnetId = 1;
    final UInt64 slot = ONE;

    final SubnetSubscription subnetSubscription = new SubnetSubscription(subnetId, slot);
    final tech.pegasys.teku.api.schema.SubnetSubscription schemaSubnetSubscription =
        new tech.pegasys.teku.api.schema.SubnetSubscription(subnetId, slot);

    final ArgumentCaptor<Set<tech.pegasys.teku.api.schema.SubnetSubscription>> argumentCaptor =
        ArgumentCaptor.forClass(Set.class);

    final SafeFuture<Void> result =
        apiHandler.subscribeToPersistentSubnets(Set.of(subnetSubscription));
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(apiClient).subscribeToPersistentSubnets(argumentCaptor.capture());

    final Set<tech.pegasys.teku.api.schema.SubnetSubscription> request = argumentCaptor.getValue();
    assertThat(request).hasSize(1);
    assertThat(request.stream().findFirst().orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(schemaSubnetSubscription);
  }

  @Test
  void shouldRetryAfterDelayWhenRequestRateLimited() {
    when(typeDefClient.getGenesis()).thenThrow(new RateLimitedException("/fork"));

    final SafeFuture<Optional<GenesisData>> result = apiHandler.getGenesisData();

    for (int i = 0; i < MAX_RATE_LIMITING_RETRIES; i++) {
      asyncRunner.executeQueuedActions();
      assertThat(result).isNotDone();
      verify(typeDefClient, times(i + 1)).getGenesis();
    }

    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(RateLimitedException.class);
  }

  @Test
  public void registerValidators_InvokeApiWithCorrectRequest() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    final SafeFuture<Void> result = apiHandler.registerValidators(validatorRegistrations);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(typeDefClient).registerValidators(validatorRegistrations);
  }

  @Test
  public void checkValidatorsDoppelganger_InvokeApiWithCorrectRequest()
      throws ExecutionException, InterruptedException {
    final List<UInt64> validatorIndices =
        List.of(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        apiHandler.getValidatorsLiveness(validatorIndices, epoch);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    assertThat(result.get()).isEmpty();
    verify(apiClient).sendValidatorsLiveness(epoch, validatorIndices);
  }

  @Test
  public void checkValidatorsDoppelgangerShouldReturnDoppelgangerDetectionResult()
      throws ExecutionException, InterruptedException {
    final UInt64 firstIndex = dataStructureUtil.randomUInt64();
    final UInt64 secondIndex = dataStructureUtil.randomUInt64();
    final UInt64 thirdIndex = dataStructureUtil.randomUInt64();

    final UInt64 epoch = dataStructureUtil.randomEpoch();

    List<UInt64> validatorIndices = List.of(firstIndex, secondIndex, thirdIndex);

    List<ValidatorLiveness> validatorLivenesses =
        List.of(
            new ValidatorLiveness(firstIndex, false),
            new ValidatorLiveness(secondIndex, true),
            new ValidatorLiveness(thirdIndex, true));
    PostValidatorLivenessResponse postValidatorLivenessResponse =
        new PostValidatorLivenessResponse(validatorLivenesses);
    when(apiClient.sendValidatorsLiveness(any(), any()))
        .thenReturn(Optional.of(postValidatorLivenessResponse));

    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        apiHandler.getValidatorsLiveness(validatorIndices, epoch);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    assertThat(result.get()).isPresent();
    List<ValidatorLivenessAtEpoch> validatorLivenessAtEpochesResult = result.get().get();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, firstIndex)).isFalse();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, secondIndex)).isTrue();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, thirdIndex)).isTrue();
    verify(apiClient).sendValidatorsLiveness(epoch, validatorIndices);
  }

  private boolean validatorIsLive(
      List<ValidatorLivenessAtEpoch> validatorLivenessAtEpoches, UInt64 validatorIndex) {
    return validatorLivenessAtEpoches.stream()
        .anyMatch(
            validatorLivenessAtEpoch ->
                validatorLivenessAtEpoch.getIndex().equals(validatorIndex)
                    && validatorLivenessAtEpoch.isLive());
  }

  private <T> Optional<T> unwrapToOptional(SafeFuture<Optional<T>> future) {
    try {
      asyncRunner.executeQueuedActions();
      return Waiter.waitFor(future);
    } catch (Exception e) {
      fail("Error unwrapping optional from SafeFuture", e);
      throw new RuntimeException(e);
    }
  }

  private <T> T unwrapToValue(SafeFuture<Optional<T>> future) {
    try {
      asyncRunner.executeQueuedActions();
      return Waiter.waitFor(future).orElseThrow();
    } catch (Exception e) {
      fail("Error unwrapping value from SafeFuture", e);
      throw new RuntimeException(e);
    }
  }

  private ValidatorResponse validatorResponse(final long index, final BLSPublicKey publicKey) {
    return new ValidatorResponse(
        UInt64.valueOf(index),
        dataStructureUtil.randomUInt64(),
        ValidatorStatus.active_ongoing,
        new Validator(
            new BLSPubKey(publicKey),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH));
  }

  private StateValidatorData stateValidatorData(final long index, final BLSPublicKey publicKey) {
    final tech.pegasys.teku.spec.datastructures.state.Validator validator =
        new tech.pegasys.teku.spec.datastructures.state.Validator(
            publicKey,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);
    return new StateValidatorData(
        UInt64.valueOf(index),
        dataStructureUtil.randomUInt64(),
        ValidatorStatus.active_ongoing,
        validator);
  }
}
