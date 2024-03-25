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

package tech.pegasys.teku.test.acceptance.dsl;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelValueSubstring;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockRootResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateValidatorResponse;
import tech.pegasys.teku.api.response.v1.node.SyncingResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.response.v1.validator.ValidatorLiveness;
import tech.pegasys.teku.api.response.v2.beacon.GetBlockResponseV2;
import tech.pegasys.teku.api.schema.AttestationData;
import tech.pegasys.teku.api.schema.AttesterSlashing;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;
import tech.pegasys.teku.api.schema.Checkpoint;
import tech.pegasys.teku.api.schema.IndexedAttestation;
import tech.pegasys.teku.api.schema.ProposerSlashing;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedBeaconBlockHeader;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.generator.BlsToExecutionChangeGenerator;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.test.acceptance.dsl.Eth2EventHandler.PackedMessage;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuBeaconNode extends TekuNode {

  private static final Logger LOG = LogManager.getLogger();
  public static final String LOCAL_VALIDATOR_LIVENESS_URL = "/eth/v1/validator/liveness/{epoch}";
  public static final String POST_PROPOSER_SLASHING_URL = "/eth/v1/beacon/pool/proposer_slashings";
  public static final String POST_ATTESTER_SLASHING_URL = "/eth/v1/beacon/pool/attester_slashings";
  private final TekuNodeConfig config;
  private final Spec spec;

  private TekuBeaconNode(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = tekuNodeConfig;
    this.spec = SpecFactory.create(config.getNetworkName(), config.getSpecConfigModifier());
    if (config.getConfigMap().containsKey("validator-api-enabled")) {
      container.addExposedPort(VALIDATOR_API_PORT);
    }

    container.addExposedPorts(METRICS_PORT, REST_API_PORT);
    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(REST_API_PORT)
                .forPath("/eth/v1/node/identity")
                .withStartupTimeout(Duration.ofMinutes(2)))
        .withCommand("--config-file", CONFIG_FILE_PATH);
  }

  public static TekuBeaconNode create(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    return new TekuBeaconNode(network, version, tekuNodeConfig);
  }

  public Spec getSpec() {
    return spec;
  }

  public void waitForContributionAndProofEvent() {
    waitForContributionAndProofEvent(proof -> true);
  }

  public void waitForContributionAndProofEvent(
      final Predicate<SignedContributionAndProof> condition) {
    waitFor(
        () -> {
          final List<SignedContributionAndProof> events = getContributionAndProofEvents();
          assertThat(events.stream().filter(condition).findAny())
              .describedAs(
                  "Did not find contribution and proof event matching condition in %s", events)
              .isPresent();
        });
  }

  private List<SignedContributionAndProof> getContributionAndProofEvents() {
    return maybeEventStreamListener
        .map(
            eventStreamListener ->
                eventStreamListener.getMessages().stream()
                    .filter(
                        packedMessage ->
                            packedMessage
                                .getEvent()
                                .equals(EventType.contribution_and_proof.name()))
                    .map(this::optionalProof)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private Optional<SignedContributionAndProof> optionalProof(
      final Eth2EventHandler.PackedMessage packedMessage) {
    try {
      return Optional.of(
          JSON_PROVIDER.jsonToObject(
              packedMessage.getMessageEvent().getData(), SignedContributionAndProof.class));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }

  public void waitForGenesis() {
    LOG.debug("Wait for genesis");
    waitFor(this::fetchGenesisTime);
  }

  public void waitForBlockAtOrAfterSlot(final long slot) {
    if (maybeEventStreamListener.isEmpty()) {
      startEventListener(EventType.head);
    }
    waitFor(
        () ->
            assertThat(
                    getSlotsFromHeadEvents().stream()
                        .filter(v -> v.isGreaterThanOrEqualTo(slot))
                        .count())
                .isGreaterThan(0));
  }

  private List<UInt64> getSlotsFromHeadEvents() {
    return maybeEventStreamListener.orElseThrow().getMessages().stream()
        .filter(packedMessage -> packedMessage.getEvent().equals(EventType.head.name()))
        .map(this::getSlotFromHeadEvent)
        .flatMap(Optional::stream)
        .collect(toList());
  }

  private Optional<UInt64> getSlotFromHeadEvent(
      final Eth2EventHandler.PackedMessage packedMessage) {
    try {
      return Optional.of(
          JSON_PROVIDER.jsonToObject(packedMessage.getMessageEvent().getData(), HeadEvent.class)
              .slot);
    } catch (JsonProcessingException e) {
      LOG.error("Failed to process head event", e);
      return Optional.empty();
    }
  }

  public void checkValidatorLiveness(
      final int epoch, final int totalValidatorCount, ValidatorLivenessExpectation... args)
      throws IOException {
    final List<UInt64> validators = new ArrayList<>();
    for (UInt64 i = UInt64.ZERO; i.isLessThan(totalValidatorCount); i = i.increment()) {
      validators.add(i);
    }
    final Object2BooleanMap<UInt64> data =
        getValidatorLivenessAtEpoch(UInt64.valueOf(epoch), validators);
    for (ValidatorLivenessExpectation expectation : args) {
      expectation.verify(data);
    }
  }

  private Object2BooleanMap<UInt64> getValidatorLivenessAtEpoch(
      final UInt64 epoch, List<UInt64> validators) throws IOException {

    final String response =
        httpClient.post(
            getRestApiUrl(),
            getValidatorLivenessUrl(epoch),
            JSON_PROVIDER.objectToJSON(validators));
    final PostValidatorLivenessResponse livenessResponse =
        JSON_PROVIDER.jsonToObject(response, PostValidatorLivenessResponse.class);
    final Object2BooleanMap<UInt64> output = new Object2BooleanOpenHashMap<UInt64>();
    for (ValidatorLiveness entry : livenessResponse.data) {
      output.put(entry.index, entry.isLive);
    }
    return output;
  }

  public void postProposerSlashing(
      final UInt64 slot, final UInt64 index, final BLSSecretKey secretKey) throws IOException {
    final ForkInfo forkInfo = getForkInfo(slot);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final SignedBeaconBlockHeader header1 =
        randomSignedBeaconBlockHeader(slot, index, secretKey, signingRootUtil, forkInfo);
    final SignedBeaconBlockHeader header2 =
        randomSignedBeaconBlockHeader(slot, index, secretKey, signingRootUtil, forkInfo);
    final ProposerSlashing proposerSlashing = new ProposerSlashing(header1, header2);
    final String body = JSON_PROVIDER.objectToJSON(proposerSlashing);
    httpClient.post(getRestApiUrl(), POST_PROPOSER_SLASHING_URL, body);
  }

  private ForkInfo getForkInfo(final UInt64 slot) throws IOException {
    final Fork fork = spec.getForkSchedule().getFork(spec.computeEpochAtSlot(slot));
    final Bytes32 genesisValidatorRoot = fetchGenesis().getGenesisValidatorsRoot();
    return new ForkInfo(fork, genesisValidatorRoot);
  }

  public void postAttesterSlashing(
      final UInt64 slashingSlot,
      final UInt64 slashedIndex,
      final BLSSecretKey slashedValidatorSecretKey)
      throws IOException {
    final ForkInfo forkInfo = getForkInfo(slashingSlot);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final IndexedAttestation indexedAttestation1 =
        randomIndexedAttestation(
            slashingSlot, slashedIndex, slashedValidatorSecretKey, signingRootUtil, forkInfo);
    final IndexedAttestation indexedAttestation2 =
        randomIndexedAttestation(
            slashingSlot, slashedIndex, slashedValidatorSecretKey, signingRootUtil, forkInfo);
    final AttesterSlashing attesterSlashing =
        new AttesterSlashing(indexedAttestation1, indexedAttestation2);
    final String body = JSON_PROVIDER.objectToJSON(attesterSlashing);
    httpClient.post(getRestApiUrl(), POST_ATTESTER_SLASHING_URL, body);
  }

  private static IndexedAttestation randomIndexedAttestation(
      final UInt64 slot,
      final UInt64 index,
      final BLSSecretKey secretKey,
      final SigningRootUtil signingRootUtil,
      final ForkInfo forkInfo) {
    final AttestationData attestationData =
        new AttestationData(
            slot,
            index,
            Bytes32.random(),
            new Checkpoint(UInt64.valueOf(1), Bytes32.random()),
            new Checkpoint(UInt64.valueOf(2), Bytes32.random()));
    final BLSSignature blsSignature1 =
        new BLSSignature(
            BLS.sign(
                secretKey,
                signingRootUtil.signingRootForSignAttestationData(
                    attestationData.asInternalAttestationData(), forkInfo)));
    return new IndexedAttestation(List.of(index), attestationData, blsSignature1);
  }

  private SignedBeaconBlockHeader randomSignedBeaconBlockHeader(
      final UInt64 slot,
      final UInt64 index,
      final BLSSecretKey secretKey,
      final SigningRootUtil signingRootUtil,
      final ForkInfo forkInfo) {
    final BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(slot, index, Bytes32.random(), Bytes32.random(), Bytes32.random());

    final Bytes blockHeaderSigningRoot =
        signingRootUtil.signingRootForSignBlockHeader(
            beaconBlockHeader.asInternalBeaconBlockHeader(), forkInfo);
    final BLSSignature blsSignature = new BLSSignature(BLS.sign(secretKey, blockHeaderSigningRoot));
    return new SignedBeaconBlockHeader(beaconBlockHeader, blsSignature);
  }

  public void submitBlsToExecutionChange(
      final int validatorIndex,
      final BLSKeyPair validatorKeyPair,
      final Eth1Address executionAddress)
      throws Exception {
    final int currentEpoch = getCurrentEpoch();

    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        new BlsToExecutionChangeGenerator(spec, getGenesisValidatorsRoot())
            .createAndSign(
                UInt64.valueOf(validatorIndex),
                validatorKeyPair,
                executionAddress,
                UInt64.valueOf(currentEpoch));

    final DeserializableTypeDefinition<List<SignedBlsToExecutionChange>> jsonTypeDefinition =
        DeserializableTypeDefinition.listOf(
            SchemaDefinitionsCapella.required(
                    spec.atEpoch(UInt64.valueOf(currentEpoch)).getSchemaDefinitions())
                .getSignedBlsToExecutionChangeSchema()
                .getJsonTypeDefinition());
    final String body = JsonUtil.serialize(List.of(signedBlsToExecutionChange), jsonTypeDefinition);

    httpClient.post(getRestApiUrl(), "/eth/v1/beacon/pool/bls_to_execution_changes", body);
  }

  public void waitForBlsToExecutionChangeEventForValidator(int validatorIndex) {
    if (maybeEventStreamListener.isEmpty()) {
      fail(
          "Must start listening to events before waiting for them... Try calling TekuNode.startEventListener(..)!");
    }

    if (!maybeEventStreamListener.get().isReady()) {
      fail(
          "Event stream listener should have been ready, but wasn't! Logs:\n"
              + container.getLogs());
    }

    waitFor(
        () -> {
          final List<tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange>
              blsToExecutionChanges =
                  getEventsOfTypeFromEventStream(
                      EventType.bls_to_execution_change, this::mapBlsToExecutionChangeFromEvent);
          assertThat(blsToExecutionChanges.stream())
              .anyMatch(m -> UInt64.valueOf(validatorIndex).equals(m.message.validatorIndex));
        });
  }

  private SyncingResponse fetchSyncStatus() throws IOException {
    String syncingData = httpClient.get(getRestApiUrl(), "/eth/v1/node/syncing");
    return JSON_PROVIDER.jsonToObject(syncingData, SyncingResponse.class);
  }

  private <T> List<T> getEventsOfTypeFromEventStream(
      final EventType type, final Function<PackedMessage, T> mapperFn) {
    return maybeEventStreamListener
        .map(
            eventStreamListener ->
                eventStreamListener.getMessages().stream()
                    .filter(packedMessage -> packedMessage.getEvent().equals(type.name()))
                    .map(mapperFn)
                    .collect(toList()))
        .orElseGet(List::of);
  }

  private tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange
      mapBlsToExecutionChangeFromEvent(final Eth2EventHandler.PackedMessage packedMessage) {
    try {
      return JSON_PROVIDER.jsonToObject(
          packedMessage.getMessageEvent().getData(),
          tech.pegasys.teku.api.schema.capella.SignedBlsToExecutionChange.class);
    } catch (JsonProcessingException e) {
      LOG.error("Failed to process bls_to_execution_change event", e);
      return null;
    }
  }

  private String getValidatorLivenessUrl(final UInt64 epoch) {
    return LOCAL_VALIDATOR_LIVENESS_URL.replace("{epoch}", epoch.toString());
  }

  public void waitForGenesisTime(final UInt64 expectedGenesisTime) {
    waitFor(() -> assertThat(fetchGenesisTime()).isEqualTo(expectedGenesisTime));
  }

  private GenesisData fetchGenesis() throws IOException {
    String genesisTime = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/genesis");
    final GetGenesisResponse response =
        JSON_PROVIDER.jsonToObject(genesisTime, GetGenesisResponse.class);
    return response.data;
  }

  private UInt64 fetchGenesisTime() throws IOException {
    return fetchGenesis().genesisTime;
  }

  public UInt64 getGenesisTime() throws IOException {
    waitForGenesis();
    return fetchGenesisTime();
  }

  public Bytes32 getGenesisValidatorsRoot() throws IOException {
    return fetchGenesis().getGenesisValidatorsRoot();
  }

  public void waitForNewBlock() {
    final Bytes32 startingBlockRoot = waitForBeaconHead(null);
    waitFor(() -> assertThat(fetchBeaconHeadRoot().orElseThrow()).isNotEqualTo(startingBlockRoot));
  }

  public void waitForOptimisticBlock() {
    waitForBeaconHead(true);
  }

  public void waitForNonOptimisticBlock() {
    waitForBeaconHead(false);
  }

  public void waitForNewFinalization() {
    UInt64 startingFinalizedEpoch = waitForChainHead().finalized.epoch;
    LOG.debug("Wait for finalized block");
    waitFor(
        () ->
            assertThat(fetchStateFinalityCheckpoints().orElseThrow().finalized.epoch)
                .isNotEqualTo(startingFinalizedEpoch),
        9,
        MINUTES);
  }

  public void waitForMilestone(final SpecMilestone expectedMilestone) {
    waitForLogMessageContaining("Activating network upgrade: " + expectedMilestone.name());
  }

  public void waitForNonDefaultExecutionPayload() {
    LOG.debug("Wait for a block containing a non default execution payload");

    waitFor(
        () -> {
          final Optional<SignedBlock> block = fetchHeadBlock();
          assertThat(block).isPresent();
          assertThat(block.get()).isInstanceOf(SignedBeaconBlockBellatrix.class);

          final SignedBeaconBlockBellatrix bellatrixBlock =
              (SignedBeaconBlockBellatrix) block.get();
          final ExecutionPayload executionPayload =
              bellatrixBlock
                  .getMessage()
                  .getBody()
                  .executionPayload
                  .asInternalExecutionPayload(spec, bellatrixBlock.getMessage().slot);
          assertThat(executionPayload.isDefault()).describedAs("Is default payload").isFalse();
          LOG.debug(
              "Non default execution payload found at slot " + bellatrixBlock.getMessage().slot);
        },
        5,
        MINUTES);
  }

  public void waitForGenesisWithNonDefaultExecutionPayload() {
    LOG.debug("Wait for genesis block containing a non default execution payload");

    waitFor(
        () -> {
          final Optional<BeaconState> maybeState = fetchState("genesis");
          assertThat(maybeState).isPresent();
          assertThat(maybeState.get().toVersionBellatrix()).isPresent();
          final BeaconStateBellatrix genesisState =
              maybeState.get().toVersionBellatrix().orElseThrow();
          assertThat(genesisState.getLatestExecutionPayloadHeader().isDefault())
              .describedAs("Is latest execution payload header a default payload header")
              .isFalse();
        });
  }

  public void waitForFullSyncCommitteeAggregate() {
    LOG.debug("Wait for full sync committee aggregates");
    waitFor(
        () -> {
          final Optional<SignedBlock> block = fetchHeadBlock();
          assertThat(block).isPresent();
          assertThat(block.get()).isInstanceOf(SignedBeaconBlockAltair.class);

          final SignedBeaconBlockAltair altairBlock = (SignedBeaconBlockAltair) block.get();
          final int syncCommitteeSize = spec.getSyncCommitteeSize(altairBlock.getMessage().slot);
          final SszBitvectorSchema<SszBitvector> syncCommitteeSchema =
              SszBitvectorSchema.create(syncCommitteeSize);

          final Bytes syncCommitteeBits =
              altairBlock.getMessage().getBody().syncAggregate.syncCommitteeBits;
          final int actualSyncBitCount =
              syncCommitteeSchema.sszDeserialize(syncCommitteeBits).getBitCount();
          final double percentageOfBitsSet =
              actualSyncBitCount == syncCommitteeSize
                  ? 1.0
                  : actualSyncBitCount / (double) syncCommitteeSize;
          assertThat(percentageOfBitsSet >= 1.0)
              .overridingErrorMessage(
                  "Sync committee bits are only %s%% full, expecting %s%%: %s",
                  percentageOfBitsSet * 100, 100, syncCommitteeBits)
              .isTrue();
        },
        5,
        MINUTES);
  }

  public void waitUntilInSyncWith(final TekuBeaconNode targetNode) {
    LOG.debug("Wait for {} to sync to {}", nodeAlias, targetNode.nodeAlias);
    waitFor(
        () -> {
          final Optional<Bytes32> beaconHead = fetchBeaconHeadRoot();
          assertThat(beaconHead).isPresent();
          final Optional<Bytes32> targetBeaconHead = targetNode.fetchBeaconHeadRoot();
          assertThat(targetBeaconHead).isPresent();
          assertThat(beaconHead).isEqualTo(targetBeaconHead);
        },
        5,
        MINUTES);
  }

  private Bytes32 waitForBeaconHead(final Boolean optimistic) {
    LOG.debug("Waiting for beacon head");
    final AtomicReference<Bytes32> beaconHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<Pair<Bytes32, Boolean>> beaconHeadRootData = fetchBeaconHeadRootData();
          assertThat(beaconHeadRootData).isPresent();
          if (optimistic != null) {
            assertEquals(optimistic, beaconHeadRootData.get().getRight());
          }
          beaconHead.set(beaconHeadRootData.get().getLeft());
        });
    LOG.debug("Retrieved beacon head: {}", beaconHead.get());
    return beaconHead.get();
  }

  private Optional<Pair<Bytes32, Boolean>> fetchBeaconHeadRootData() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/blocks/head/root");
    if (result.isEmpty()) {
      return Optional.empty();
    }

    final GetBlockRootResponse response =
        JSON_PROVIDER.jsonToObject(result, GetBlockRootResponse.class);

    return Optional.of(Pair.of(response.data.root, response.execution_optimistic));
  }

  private Optional<Bytes32> fetchBeaconHeadRoot() throws IOException {
    return fetchBeaconHeadRootData().map(Pair::getLeft);
  }

  private FinalityCheckpointsResponse waitForChainHead() {
    LOG.debug("Waiting for chain head");
    final AtomicReference<FinalityCheckpointsResponse> chainHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<FinalityCheckpointsResponse> fetchCheckpoints =
              fetchStateFinalityCheckpoints();
          assertThat(fetchCheckpoints).isPresent();
          chainHead.set(fetchCheckpoints.get());
        });
    LOG.debug("Retrieved chain head: {}", chainHead.get());
    return chainHead.get();
  }

  private Optional<FinalityCheckpointsResponse> fetchStateFinalityCheckpoints() throws IOException {
    final String result =
        httpClient.get(getRestApiUrl(), "/eth/v1/beacon/states/head/finality_checkpoints");
    if (result.isEmpty()) {
      return Optional.empty();
    }
    final GetStateFinalityCheckpointsResponse response =
        JSON_PROVIDER.jsonToObject(result, GetStateFinalityCheckpointsResponse.class);
    return Optional.of(response.data);
  }

  private Optional<SignedBlock> fetchHeadBlock() throws IOException {
    final String blockId = "head";
    final String result = httpClient.get(getRestApiUrl(), "/eth/v2/beacon/blocks/" + blockId);
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(JSON_PROVIDER.jsonToObject(result, GetBlockResponseV2.class).data);
    }
  }

  private Optional<BeaconState> fetchHeadState() throws IOException {
    return fetchState("head");
  }

  private Optional<BeaconState> fetchState(final String stateId) throws IOException {
    final Bytes result =
        httpClient.getAsBytes(
            getRestApiUrl(),
            "/eth/v2/debug/beacon/states/" + stateId,
            Map.of("Accept", "application/octet-stream"));
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(spec.deserializeBeaconState(result));
    }
  }

  private Optional<Validator> fetchValidator(final int validatorId) throws IOException {
    final String result =
        httpClient.get(
            getRestApiUrl(),
            "/eth/v1/beacon/states/head/validators/" + validatorId,
            Map.of("Accept", "application/octet-stream"));
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      final GetStateValidatorResponse response =
          JSON_PROVIDER.jsonToObject(result, GetStateValidatorResponse.class);
      return Optional.of(response.data.validator.asInternalValidator());
    }
  }

  public void waitForValidators(int numberOfValidators) {
    waitFor(
        () -> {
          Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeState).isPresent();
          BeaconState state = maybeState.get();
          assertThat(state.getValidators().size()).isEqualTo(numberOfValidators);
        });
  }

  public void waitForValidatorWithCredentials(
      final int validatorIndex, final Eth1Address executionAddress) {
    waitFor(
        () -> {
          final Optional<Validator> maybeValidator = fetchValidator(validatorIndex);
          assertThat(maybeValidator).isPresent();
          Validator validator = maybeValidator.get();
          final Bytes32 withdrawalCredentials = validator.getWithdrawalCredentials();
          assertThat(withdrawalCredentials)
              .isEqualTo(
                  BlockProcessorCapella.getWithdrawalAddressFromEth1Address(executionAddress));
        });
  }

  public void waitForAttestationBeingGossiped(
      int validatorSeparationIndex, int totalValidatorCount) {
    List<UInt64> node1Validators =
        IntStream.range(0, validatorSeparationIndex).mapToObj(UInt64::valueOf).toList();
    List<UInt64> node2Validators =
        IntStream.range(validatorSeparationIndex, totalValidatorCount)
            .mapToObj(UInt64::valueOf)
            .toList();
    waitFor(
        () -> {
          final Optional<SignedBlock> maybeBlock = fetchHeadBlock();
          final Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeBlock).isPresent();
          assertThat(maybeState).isPresent();
          SignedBeaconBlock block = (SignedBeaconBlock) maybeBlock.get();
          BeaconState state = maybeState.get();

          // Check that the fetched block and state are in sync
          assertThat(state.getLatestBlockHeader().getParentRoot())
              .isEqualTo(block.getMessage().parent_root);

          UInt64 proposerIndex = block.getMessage().proposer_index;

          Set<UInt64> attesterIndicesInAttestations =
              block.getMessage().getBody().attestations.stream()
                  .map(a -> spec.getAttestingIndices(state, a.asInternalAttestation(spec)))
                  .flatMap(Collection::stream)
                  .map(UInt64::valueOf)
                  .collect(toSet());

          if (node1Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node2Validators::contains))
                .isTrue();
          } else if (node2Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node1Validators::contains))
                .isTrue();
          } else {
            throw new IllegalStateException("Proposer index greater than total validator count");
          }
        },
        2,
        MINUTES);
  }

  public void addValidators(final ValidatorKeystores additionalKeystores) {
    LOG.debug("Adding {} validators", additionalKeystores.getValidatorCount());
    container.expandTarballToContainer(additionalKeystores.getTarball(), WORKING_DIRECTORY);
    container
        .getDockerClient()
        .killContainerCmd(container.getContainerId())
        .withSignal("HUP")
        .exec();
  }

  String getMultiAddr() {
    return "/dns4/" + nodeAlias + "/tcp/" + P2P_PORT + "/p2p/" + config.getPeerId();
  }

  public String getBeaconRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + REST_API_PORT;
    LOG.debug("Node REST url: " + url);
    return url;
  }

  @Override
  public TekuNodeConfig getConfig() {
    return config;
  }

  @Override
  public void captureDebugArtifacts(final File artifactDir) {
    if (container.isRunning()) {
      copyDirectoryToTar(WORKING_DIRECTORY, new File(artifactDir, nodeAlias + ".tar"));
    } else {
      // Can't capture artifacts if it's not running but then it probably didn't cause the failure
      LOG.debug("Not capturing artifacts from {} because it is not running", nodeAlias);
    }
  }

  public void waitForAggregateGossipReceived() {
    waitForMetric(
        withNameEqualsTo("libp2p_gossip_messages_total"),
        withLabelValueSubstring("topic", "beacon_aggregate_and_proof"),
        withValueGreaterThan(0));
  }

  public void expectNodeNotSyncing() throws IOException {
    assertThat(fetchSyncStatus().data.isSyncing).isFalse();
  }

  public void expectElOffline() throws IOException {
    assertThat(fetchSyncStatus().data.elOffline).isTrue();
  }

  public void expectElOnline() throws IOException {
    assertThat(fetchSyncStatus().data.elOffline).isFalse();
  }
}
