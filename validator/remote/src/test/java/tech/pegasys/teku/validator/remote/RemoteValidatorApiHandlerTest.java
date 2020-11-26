/*
 * Copyright 2020 ConsenSys AG.
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
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_PUBLIC_KEY_BATCH_SIZE;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_RATE_LIMITING_RETRIES;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.ValidatorResponse;
import tech.pegasys.teku.api.response.v1.validator.GetProposerDutiesResponse;
import tech.pegasys.teku.api.response.v1.validator.PostAttesterDutiesResponse;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.AttesterDuties;
import tech.pegasys.teku.validator.api.AttesterDuty;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.ProposerDuties;
import tech.pegasys.teku.validator.api.ProposerDuty;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.apiclient.SchemaObjectsTestFixture;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorRestApiClient;

class RemoteValidatorApiHandlerTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final SchemaObjectsTestFixture schemaObjects = new SchemaObjectsTestFixture();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ValidatorRestApiClient apiClient = mock(ValidatorRestApiClient.class);

  private RemoteValidatorApiHandler apiHandler;

  @BeforeEach
  public void beforeEach() {
    apiHandler = new RemoteValidatorApiHandler(apiClient, asyncRunner);
  }

  @Test
  public void getForkInfo_WhenPresent_ReturnsValue() {
    final Fork fork = dataStructureUtil.randomFork();
    when(apiClient.getFork()).thenReturn(Optional.of(new tech.pegasys.teku.api.schema.Fork(fork)));
    SafeFuture<Optional<Fork>> future = apiHandler.getFork();

    assertThat(unwrapToValue(future)).isEqualTo(fork);
  }

  @Test
  public void getForkInfo_WhenNotPresent_ReturnsEmpty() {
    when(apiClient.getFork()).thenReturn(Optional.empty());
    SafeFuture<Optional<Fork>> future = apiHandler.getFork();

    assertThat(unwrapToOptional(future)).isNotPresent();
  }

  @Test
  public void getGenesisTime_WhenPresent_ReturnsValue() {
    final UInt64 genesisTime = dataStructureUtil.randomUInt64();
    when(apiClient.getGenesis())
        .thenReturn(
            Optional.of(
                new GetGenesisResponse(
                    new GenesisData(
                        genesisTime,
                        dataStructureUtil.randomBytes32(),
                        dataStructureUtil.randomBytes4()))));

    SafeFuture<Optional<tech.pegasys.teku.datastructures.genesis.GenesisData>> future =
        apiHandler.getGenesisData();

    assertThat(unwrapToValue(future).getGenesisTime()).isEqualTo(genesisTime);
  }

  @Test
  public void getGenesisTime_WhenNotPresent_ReturnsEmpty() {
    when(apiClient.getGenesis()).thenReturn(Optional.empty());

    SafeFuture<Optional<tech.pegasys.teku.datastructures.genesis.GenesisData>> future =
        apiHandler.getGenesisData();

    assertThat(unwrapToOptional(future)).isNotPresent();
  }

  @Test
  void getValidatorIndices_WithEmptyPublicKeys_ReturnsEmptyMap() {
    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(emptyList());

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(future.join()).isEmpty();
  }

  @Test
  void getValidatorIndices_WithSmallNumberOfPublicKeys_RequestsSingleBatch() {
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());
    when(apiClient.getValidators(expectedValidatorIds))
        .thenReturn(
            Optional.of(
                List.of(
                    schemaObjects.validatorResponse(1, key1),
                    schemaObjects.validatorResponse(2, key2))));

    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(future.join()).containsOnly(entry(key1, 1), entry(key2, 2));
    verify(apiClient).getValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_WithLargeNumberOfPublicKeys_CombinesMultipleBatches() {
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

    final List<ValidatorResponse> batch1Responses =
        List.of(
            schemaObjects.validatorResponse(10, allKeys.get(0)),
            schemaObjects.validatorResponse(11, allKeys.get(3)));
    final List<ValidatorResponse> batch2Responses =
        List.of(
            schemaObjects.validatorResponse(20, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE)),
            schemaObjects.validatorResponse(21, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3)));
    final List<ValidatorResponse> batch3Responses =
        List.of(
            schemaObjects.validatorResponse(30, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2)),
            schemaObjects.validatorResponse(31, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3)));

    when(apiClient.getValidators(expectedBatch1)).thenReturn(Optional.of(batch1Responses));
    when(apiClient.getValidators(expectedBatch2)).thenReturn(Optional.of(batch2Responses));
    when(apiClient.getValidators(expectedBatch3)).thenReturn(Optional.of(batch3Responses));

    final SafeFuture<Map<BLSPublicKey, Integer>> future = apiHandler.getValidatorIndices(allKeys);

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(future.join())
        .containsOnly(
            entry(allKeys.get(0), 10),
            entry(allKeys.get(3), 11),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE), 20),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3), 21),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2), 30),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3), 31));
    verify(apiClient).getValidators(expectedBatch1);
    verify(apiClient).getValidators(expectedBatch2);
    verify(apiClient).getValidators(expectedBatch3);
  }

  @Test
  public void getAttestationDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(UInt64.ONE, emptyList());

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getAttestationDuties_WhenNoneFound_ReturnsEmpty() {
    when(apiClient.getAttestationDuties(any(), any()))
        .thenReturn(
            Optional.of(
                new PostAttesterDutiesResponse(
                    dataStructureUtil.randomBytes32(), Collections.emptyList())));

    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(UInt64.ONE, List.of(1234));

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
    final tech.pegasys.teku.api.response.v1.validator.AttesterDuty schemaValidatorDuties =
        new tech.pegasys.teku.api.response.v1.validator.AttesterDuty(
            new BLSPubKey(blsPublicKey),
            UInt64.valueOf(validatorIndex),
            UInt64.valueOf(committeeIndex),
            UInt64.valueOf(committeeLength),
            UInt64.valueOf(committeesAtSlot),
            UInt64.valueOf(validatorCommitteeIndex),
            UInt64.ZERO);
    final AttesterDuty expectedValidatorDuties =
        new AttesterDuty(
            blsPublicKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            validatorCommitteeIndex,
            UInt64.ZERO);

    when(apiClient.getAttestationDuties(UInt64.ONE, List.of(validatorIndex)))
        .thenReturn(
            Optional.of(
                new PostAttesterDutiesResponse(
                    dataStructureUtil.randomBytes32(), List.of(schemaValidatorDuties))));

    SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(UInt64.ONE, List.of(validatorIndex));

    AttesterDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0))
        .usingRecursiveComparison()
        .isEqualTo(expectedValidatorDuties);
  }

  @Test
  public void getProposerDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(UInt64.ONE);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenNoneFound_ReturnsEmpty() {
    when(apiClient.getProposerDuties(any()))
        .thenReturn(
            Optional.of(
                new GetProposerDutiesResponse(
                    Bytes32.fromHexString("0x1234"), Collections.emptyList())));

    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(UInt64.ONE);

    assertThat(unwrapToValue(future).getDuties()).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenFound_ReturnsDuties() {
    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final int validatorIndex = 472;
    final tech.pegasys.teku.api.response.v1.validator.ProposerDuty schemaValidatorDuties =
        new tech.pegasys.teku.api.response.v1.validator.ProposerDuty(
            new BLSPubKey(blsPublicKey), validatorIndex, UInt64.ZERO);
    final ProposerDuty expectedValidatorDuties =
        new ProposerDuty(blsPublicKey, validatorIndex, UInt64.ZERO);
    final GetProposerDutiesResponse response =
        new GetProposerDutiesResponse(
            Bytes32.fromHexString("0x1234"), List.of(schemaValidatorDuties));

    when(apiClient.getProposerDuties(UInt64.ONE)).thenReturn(Optional.of(response));

    SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(UInt64.ONE);

    ProposerDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0))
        .usingRecursiveComparison()
        .isEqualTo(expectedValidatorDuties);
    assertThat(validatorDuties.getDependentRoot()).isEqualTo(response.dependentRoot);
  }

  @Test
  public void createUnsignedAttestation_WhenNone_ReturnsEmpty() {
    when(apiClient.createUnsignedAttestation(any(), anyInt())).thenReturn(Optional.empty());

    SafeFuture<Optional<Attestation>> future = apiHandler.createUnsignedAttestation(UInt64.ONE, 0);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createUnsignedAttestation_WhenFound_ReturnsAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    when(apiClient.createUnsignedAttestation(eq(UInt64.ONE), eq(0)))
        .thenReturn(Optional.of(schemaAttestation));

    SafeFuture<Optional<Attestation>> future = apiHandler.createUnsignedAttestation(UInt64.ONE, 0);

    assertThat(unwrapToValue(future)).usingRecursiveComparison().isEqualTo(attestation);
  }

  @Test
  public void createAttestationData_WhenNone_ReturnsEmpty() {
    when(apiClient.createAttestationData(any(), anyInt())).thenReturn(Optional.empty());

    SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(UInt64.ONE, 0);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createAttestationData_WhenFound_ReturnsAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final tech.pegasys.teku.api.schema.AttestationData schemaAttestationData =
        new tech.pegasys.teku.api.schema.AttestationData(attestation.getData());

    when(apiClient.createAttestationData(eq(UInt64.ONE), eq(0)))
        .thenReturn(Optional.of(schemaAttestationData));

    SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(UInt64.ONE, 0);

    assertThat(unwrapToValue(future)).usingRecursiveComparison().isEqualTo(attestation.getData());
  }

  @Test
  public void sendSignedAttestation_InvokeApiWithCorrectRequest() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    ArgumentCaptor<tech.pegasys.teku.api.schema.Attestation> argumentCaptor =
        ArgumentCaptor.forClass(tech.pegasys.teku.api.schema.Attestation.class);

    apiHandler.sendSignedAttestation(attestation);
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendSignedAttestation(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(schemaAttestation);
  }

  @Test
  public void sendSignedAttestation_IgnoresValidatorIndexParameter_AndInvokeApi() {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final tech.pegasys.teku.api.schema.Attestation schemaAttestation =
        new tech.pegasys.teku.api.schema.Attestation(attestation);

    ArgumentCaptor<tech.pegasys.teku.api.schema.Attestation> argumentCaptor =
        ArgumentCaptor.forClass(tech.pegasys.teku.api.schema.Attestation.class);

    apiHandler.sendSignedAttestation(attestation, Optional.of(1));
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendSignedAttestation(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(schemaAttestation);
  }

  @Test
  public void createUnsignedBlock_WhenNoneFound_ReturnsEmpty() {
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();

    SafeFuture<Optional<BeaconBlock>> future =
        apiHandler.createUnsignedBlock(UInt64.ONE, blsSignature, Optional.of(Bytes32.random()));

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenFound_ReturnsBlock() {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(UInt64.ONE);
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    final tech.pegasys.teku.api.schema.BLSSignature schemaBlsSignature =
        new tech.pegasys.teku.api.schema.BLSSignature(blsSignature);
    final tech.pegasys.teku.api.schema.BeaconBlock schemaBeaconBlock =
        new tech.pegasys.teku.api.schema.BeaconBlock(beaconBlock);

    when(apiClient.createUnsignedBlock(
            eq(beaconBlock.getSlot()), refEq(schemaBlsSignature), eq(graffiti)))
        .thenReturn(Optional.of(schemaBeaconBlock));

    SafeFuture<Optional<BeaconBlock>> future =
        apiHandler.createUnsignedBlock(UInt64.ONE, blsSignature, graffiti);

    assertThat(unwrapToValue(future)).usingRecursiveComparison().isEqualTo(beaconBlock);
  }

  @Test
  public void sendSignedBlock_InvokeApiWithCorrectRequest() {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(UInt64.ONE);
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SignedBeaconBlock signedBeaconBlock = new SignedBeaconBlock(beaconBlock, signature);
    final SendSignedBlockResult expectedResult = SendSignedBlockResult.success(Bytes32.ZERO);

    final tech.pegasys.teku.api.schema.SignedBeaconBlock schemaSignedBlock =
        new tech.pegasys.teku.api.schema.SignedBeaconBlock(signedBeaconBlock);

    when(apiClient.sendSignedBlock(any())).thenReturn(expectedResult);

    ArgumentCaptor<tech.pegasys.teku.api.schema.SignedBeaconBlock> argumentCaptor =
        ArgumentCaptor.forClass(tech.pegasys.teku.api.schema.SignedBeaconBlock.class);

    final SafeFuture<SendSignedBlockResult> result = apiHandler.sendSignedBlock(signedBeaconBlock);
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendSignedBlock(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue()).usingRecursiveComparison().isEqualTo(schemaSignedBlock);
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

    assertThat(unwrapToValue(future)).usingRecursiveComparison().isEqualTo(attestation);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void sendSAggregateAndProof_InvokeApiWithCorrectRequest() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SignedAggregateAndProof signedAggregateAndProof =
        new SignedAggregateAndProof(aggregateAndProof, signature);

    tech.pegasys.teku.api.schema.SignedAggregateAndProof schemaSignedAggAndProof =
        new tech.pegasys.teku.api.schema.SignedAggregateAndProof(signedAggregateAndProof);

    ArgumentCaptor<List<tech.pegasys.teku.api.schema.SignedAggregateAndProof>> argumentCaptor =
        ArgumentCaptor.forClass(List.class);

    apiHandler.sendAggregateAndProof(signedAggregateAndProof);
    asyncRunner.executeQueuedActions();

    verify(apiClient).sendAggregateAndProofs(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue())
        .usingRecursiveComparison()
        .isEqualTo(List.of(schemaSignedAggAndProof));
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_InvokeApi() {
    final int validatorIndex = 3;
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = UInt64.ONE;
    final boolean isAggregator = true;
    final UInt64 committeesAtSlot = UInt64.valueOf(23);
    final List<CommitteeSubscriptionRequest> requests =
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, isAggregator));
    apiHandler.subscribeToBeaconCommittee(requests);
    asyncRunner.executeQueuedActions();

    verify(apiClient).subscribeToBeaconCommittee(requests);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void subscribeToPersistentSubnets_InvokeApi() {
    final int subnetId = 1;
    final UInt64 slot = UInt64.ONE;

    final SubnetSubscription subnetSubscription = new SubnetSubscription(subnetId, slot);
    final tech.pegasys.teku.api.schema.SubnetSubscription schemaSubnetSubscription =
        new tech.pegasys.teku.api.schema.SubnetSubscription(subnetId, slot);

    ArgumentCaptor<Set<tech.pegasys.teku.api.schema.SubnetSubscription>> argumentCaptor =
        ArgumentCaptor.forClass(Set.class);

    apiHandler.subscribeToPersistentSubnets(Set.of(subnetSubscription));
    asyncRunner.executeQueuedActions();

    verify(apiClient).subscribeToPersistentSubnets(argumentCaptor.capture());

    final Set<tech.pegasys.teku.api.schema.SubnetSubscription> request = argumentCaptor.getValue();
    assertThat(request).hasSize(1);
    assertThat(request.stream().findFirst().orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(schemaSubnetSubscription);
  }

  @Test
  void shouldRetryAfterDelayWhenRequestRateLimited() {
    when(apiClient.getFork()).thenThrow(new RateLimitedException("/fork"));

    final SafeFuture<Optional<Fork>> result = apiHandler.getFork();

    for (int i = 0; i < MAX_RATE_LIMITING_RETRIES; i++) {
      asyncRunner.executeQueuedActions();
      assertThat(result).isNotDone();
      verify(apiClient, times(i + 1)).getFork();
    }

    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(RateLimitedException.class);
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
}
