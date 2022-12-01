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

package tech.pegasys.teku.test.acceptance.dsl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelValueSubstring;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.KEY_TYPE;
import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.PrivKey;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;
import tech.pegasys.teku.api.request.v1.validator.ValidatorLivenessRequest;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.beacon.FinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.beacon.GenesisData;
import tech.pegasys.teku.api.response.v1.beacon.GetBlockRootResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetGenesisResponse;
import tech.pegasys.teku.api.response.v1.beacon.GetStateFinalityCheckpointsResponse;
import tech.pegasys.teku.api.response.v1.validator.PostValidatorLivenessResponse;
import tech.pegasys.teku.api.response.v1.validator.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.v2.beacon.GetBlockResponseV2;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.altair.SignedBeaconBlockAltair;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.api.schema.bellatrix.SignedBeaconBlockBellatrix;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.test.acceptance.dsl.GenesisGenerator.InitialStateData;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuNode extends Node {

  private static final Logger LOG = LogManager.getLogger();
  public static final String LOCAL_VALIDATOR_LIVENESS_URL = "/eth/v1/validator/liveness/{epoch}";
  private final Config config;
  private final Spec spec;
  private Optional<EventStreamListener> maybeEventStreamListener = Optional.empty();

  private boolean started = false;
  private Set<File> configFiles;

  private TekuNode(final Network network, final DockerVersion version, final Config config) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = config;

    Consumer<SpecConfigBuilder> specConfigModifier = config.getSpecConfigModifier();
    this.spec = SpecFactory.create(config.getNetworkName(), specConfigModifier);

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withExposedPorts(REST_API_PORT, METRICS_PORT)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(REST_API_PORT)
                .forPath("/eth/v1/node/identity")
                .withStartupTimeout(Duration.ofMinutes(2)))
        .withCommand("--config-file", CONFIG_FILE_PATH);
  }

  public Spec getSpec() {
    return spec;
  }

  public static TekuNode create(
      final Network network, final DockerVersion version, final Consumer<Config> configOptions)
      throws TimeoutException, IOException {

    final Config config = new Config();
    configOptions.accept(config);

    return new TekuNode(network, version, config);
  }

  public void start() throws Exception {
    assertThat(started).isFalse();
    LOG.debug("Start node {}", nodeAlias);
    started = true;
    final Map<File, String> configFiles = config.write();
    this.configFiles = configFiles.keySet();
    configFiles.forEach(
        (localFile, targetPath) ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(localFile.getAbsolutePath()), targetPath));
    config.getTarballsToCopy().forEach(this::copyContentsToWorkingDirectory);
    container.start();
  }

  public void startEventListener(final List<EventType> eventTypes) {
    maybeEventStreamListener = Optional.of(new EventStreamListener(getEventUrl(eventTypes)));
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
    if (maybeEventStreamListener.isEmpty()) {
      return Collections.emptyList();
    }
    return maybeEventStreamListener.get().getMessages().stream()
        .filter(
            packedMessage ->
                packedMessage.getEvent().equals(EventType.contribution_and_proof.name()))
        .map(this::optionalProof)
        .flatMap(Optional::stream)
        .collect(Collectors.toList());
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
      startEventListener(List.of(EventType.head));
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
    return maybeEventStreamListener.get().getMessages().stream()
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

    final ValidatorLivenessRequest request = new ValidatorLivenessRequest(validators);
    final String response =
        httpClient.post(
            getRestApiUrl(), getValidatorLivenessUrl(epoch), JSON_PROVIDER.objectToJSON(request));
    final PostValidatorLivenessResponse livenessResponse =
        JSON_PROVIDER.jsonToObject(response, PostValidatorLivenessResponse.class);
    final Object2BooleanMap<UInt64> output = new Object2BooleanOpenHashMap<UInt64>();
    for (ValidatorLivenessAtEpoch entry : livenessResponse.data) {
      output.put(entry.index, entry.isLive);
    }
    return output;
  }

  public void submitBlsToExecutionChange(
      final SignedBlsToExecutionChange signedBlsToExecutionChange) throws Exception {

    final int currentEpoch = getCurrentEpoch();
    final DeserializableTypeDefinition<SignedBlsToExecutionChange> jsonTypeDefinition =
        SchemaDefinitionsCapella.required(
                spec.atEpoch(UInt64.valueOf(currentEpoch)).getSchemaDefinitions())
            .getSignedBlsToExecutionChangeSchema()
            .getJsonTypeDefinition();
    final String body = JsonUtil.serialize(signedBlsToExecutionChange, jsonTypeDefinition);

    httpClient.post(getRestApiUrl(), "/eth/v1/beacon/pool/bls_to_execution_changes", body);
  }

  private String getValidatorLivenessUrl(final UInt64 epoch) {
    return LOCAL_VALIDATOR_LIVENESS_URL.replace("{epoch}", epoch.toString());
  }

  public void waitForGenesisTime(final UInt64 expectedGenesisTime) {
    waitFor(() -> assertThat(fetchGenesisTime()).isEqualTo(expectedGenesisTime));
  }

  private String getEventUrl(List<EventType> events) {
    String eventTypes = "";
    if (events.size() > 0) {
      eventTypes =
          "?topics=" + events.stream().map(EventType::name).collect(Collectors.joining(","));
    }
    return getRestApiUrl() + "/eth/v1/events" + eventTypes;
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
    waitFor(() -> assertThat(fetchBeaconHeadRoot().get()).isNotEqualTo(startingBlockRoot));
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
            assertThat(fetchStateFinalityCheckpoints().get().finalized.epoch)
                .isNotEqualTo(startingFinalizedEpoch),
        9,
        MINUTES);
  }

  public void waitForMilestone(SpecMilestone expectedMilestone) {
    waitFor(
        () -> {
          final int currentEpoch = (int) getMetricValue("beacon_epoch");
          final SpecMilestone currentMilestone =
              spec.getForkSchedule().getSpecMilestoneAtEpoch(UInt64.valueOf(currentEpoch));
          assertThat(currentMilestone).isEqualTo(expectedMilestone);
          waitForNewFinalization();
        },
        2,
        TimeUnit.MINUTES);
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
          final BeaconStateBellatrix genesisState = maybeState.get().toVersionBellatrix().get();
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

  public void waitUntilInSyncWith(final TekuNode targetNode) {
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
    return fetchBlock("head");
  }

  private Optional<SignedBlock> fetchBlock(final String blockId) throws IOException {
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

  public void waitForValidators(int numberOfValidators) {
    waitFor(
        () -> {
          Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeState).isPresent();
          BeaconState state = maybeState.get();
          assertThat(state.getValidators().size()).isEqualTo(numberOfValidators);
        });
  }

  public void waitForValidatorWithCredentials(int validatorIndex, Bytes32 expectedCredentials) {
    waitFor(
        () -> {
          Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeState).isPresent();
          BeaconState state = maybeState.get();
          final Bytes32 withdrawalCredentials =
              state.getValidators().get(validatorIndex).getWithdrawalCredentials();
          assertThat(withdrawalCredentials).isEqualTo(expectedCredentials);
        });
  }

  public void waitForAttestationBeingGossiped(
      int validatorSeparationIndex, int totalValidatorCount) {
    List<UInt64> node1Validators =
        IntStream.range(0, validatorSeparationIndex).mapToObj(UInt64::valueOf).collect(toList());
    List<UInt64> node2Validators =
        IntStream.range(validatorSeparationIndex, totalValidatorCount)
            .mapToObj(UInt64::valueOf)
            .collect(toList());
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
                  .map(
                      a ->
                          spec.getAttestingIndices(
                              state,
                              a.asInternalAttestation(spec).getData(),
                              a.asInternalAttestation(spec).getAggregationBits()))
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

  public Config getConfig() {
    return config;
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

  public void waitForOwnedValidatorCount(final int expectedValidatorCount) throws IOException {
    LOG.debug("Waiting for validator count to be {}", expectedValidatorCount);
    waitFor(
        () -> {
          final double validatorCount = getMetricValue("validator_local_validator_count");
          LOG.debug("Current validator count {}", validatorCount);
          assertThat(validatorCount).isEqualTo(expectedValidatorCount);
        });
  }

  /**
   * Copies data directory from node into a temporary directory.
   *
   * @return A file containing the data directory.
   */
  public File getDataDirectoryFromContainer() throws Exception {
    File dbTar = File.createTempFile("database", ".tar");
    dbTar.deleteOnExit();
    copyDirectoryToTar(DATA_PATH, dbTar);
    return dbTar;
  }

  public void copyFileToContainer(File file, String containerPath) {
    container.withCopyFileToContainer(
        MountableFile.forHostPath(file.getAbsolutePath()), containerPath);
  }

  String getMultiAddr() {
    return "/dns4/" + nodeAlias + "/tcp/" + P2P_PORT + "/p2p/" + config.getPeerId();
  }

  public String getBeaconRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + REST_API_PORT;
    LOG.debug("Node REST url: " + url);
    return url;
  }

  private URI getRestApiUrl() {
    return URI.create("http://127.0.0.1:" + container.getMappedPort(REST_API_PORT));
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    started = false;
    maybeEventStreamListener.ifPresent(EventStreamListener::close);
    configFiles.forEach(
        configFile -> {
          if (!configFile.delete() && configFile.exists()) {
            throw new RuntimeException("Failed to delete config file: " + configFile);
          }
        });
    container.stop();
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

  public static class Config {

    public static final String DEFAULT_NETWORK_NAME = "swift";
    public static final String EE_JWT_SECRET_FILE_KEY = "ee-jwt-secret-file";
    private static final String INITIAL_STATE_FILE = "/state.ssz";

    private Optional<URL> maybeNetworkYaml = Optional.empty();
    private Optional<URL> maybeJwtFile = Optional.empty();
    private Optional<InitialStateData> maybeInitialState = Optional.empty();

    private final PrivKey privateKey = KeyKt.generateKeyPair(KEY_TYPE.SECP256K1).component1();
    private final PeerId peerId = PeerId.fromPubKey(privateKey.publicKey());
    private static final int DEFAULT_VALIDATOR_COUNT = 64;

    private String networkName = DEFAULT_NETWORK_NAME;
    private final Map<String, Object> configMap = new HashMap<>();
    private final List<File> tarballsToCopy = new ArrayList<>();

    private Consumer<SpecConfigBuilder> specConfigModifier = builder -> {};

    public Config() {
      configMap.put("network", networkName);
      configMap.put("p2p-enabled", false);
      configMap.put("p2p-discovery-enabled", false);
      configMap.put("p2p-port", P2P_PORT);
      configMap.put("p2p-advertised-port", P2P_PORT);
      configMap.put("p2p-interface", "0.0.0.0");
      configMap.put("p2p-private-key-file", PRIVATE_KEY_FILE_PATH);
      configMap.put("Xinterop-genesis-time", 0);
      configMap.put("Xinterop-owned-validator-start-index", 0);
      configMap.put("Xstartup-target-peer-count", 0);
      configMap.put("Xinterop-owned-validator-count", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-number-of-validators", DEFAULT_VALIDATOR_COUNT);
      configMap.put("Xinterop-enabled", true);
      configMap.put("validators-keystore-locking-enabled", false);
      configMap.put("rest-api-enabled", true);
      configMap.put("rest-api-port", REST_API_PORT);
      configMap.put("rest-api-docs-enabled", false);
      configMap.put("metrics-enabled", true);
      configMap.put("metrics-port", METRICS_PORT);
      configMap.put("metrics-interface", "0.0.0.0");
      configMap.put("metrics-host-allowlist", "*");
      configMap.put("data-path", DATA_PATH);
      configMap.put("eth1-deposit-contract-address", "0xdddddddddddddddddddddddddddddddddddddddd");
      configMap.put("log-destination", "console");
      configMap.put("rest-api-host-allowlist", "*");
    }

    public Config withDepositsFrom(final BesuNode eth1Node) {
      configMap.put("Xinterop-enabled", false);
      configMap.put(
          "eth1-deposit-contract-address", eth1Node.getDepositContractAddress().toString());
      configMap.put("eth1-endpoint", eth1Node.getInternalJsonRpcUrl());
      return this;
    }

    public Config withLogging(final String logging) {
      configMap.put("logging", logging);
      return this;
    }

    public Config withInteropValidators(final int startIndex, final int validatorCount) {
      configMap.put("Xinterop-owned-validator-start-index", startIndex);
      configMap.put("Xinterop-owned-validator-count", validatorCount);
      return this;
    }

    public Config withValidatorLivenessTracking() {
      configMap.put("Xbeacon-liveness-tracking-enabled", true);
      return this;
    }

    public Config withInteropNumberOfValidators(final int validatorCount) {
      configMap.put("Xinterop-number-of-validators", validatorCount);
      return this;
    }

    public Config withExternalMetricsClient(
        final ExternalMetricNode externalMetricsNode, final int intervalBetweenPublications) {
      configMap.put("metrics-publish-endpoint", externalMetricsNode.getPublicationEndpointURL());
      configMap.put("metrics-publish-interval", intervalBetweenPublications);
      return this;
    }

    public Config withValidatorKeystores(final ValidatorKeystores keystores) {
      configMap.put("Xinterop-enabled", false);
      tarballsToCopy.add(keystores.getTarball());
      configMap.put(
          "validator-keys",
          WORKING_DIRECTORY
              + keystores.getKeysDirectoryName()
              + ":"
              + WORKING_DIRECTORY
              + keystores.getPasswordsDirectoryName());
      return this;
    }

    public Config withGenesisTime(int time) {
      configMap.put("Xinterop-genesis-time", time);
      return this;
    }

    public Config withNetwork(String networkName) {
      this.networkName = networkName;
      configMap.put("network", networkName);
      return this;
    }

    public Config withNetwork(final URL networkYaml, final String networkName) {
      this.maybeNetworkYaml = Optional.of(networkYaml);
      this.networkName = networkName;
      configMap.put("network", NETWORK_FILE_PATH);
      return this;
    }

    public Config withAltairEpoch(final UInt64 altairForkEpoch) {
      configMap.put("Xnetwork-altair-fork-epoch", altairForkEpoch.toString());
      specConfigModifier =
          specConfigModifier.andThen(
              specConfigBuilder ->
                  specConfigBuilder.altairBuilder(
                      altairBuilder -> altairBuilder.altairForkEpoch(altairForkEpoch)));
      return this;
    }

    public Config withBellatrixEpoch(final UInt64 bellatrixForkEpoch) {
      configMap.put("Xnetwork-bellatrix-fork-epoch", bellatrixForkEpoch.toString());
      specConfigModifier =
          specConfigModifier.andThen(
              specConfigBuilder ->
                  specConfigBuilder.bellatrixBuilder(
                      bellatrixBuilder -> bellatrixBuilder.bellatrixForkEpoch(bellatrixForkEpoch)));
      return this;
    }

    public Config withCapellaEpoch(final UInt64 capellaForkEpoch) {
      configMap.put("Xnetwork-capella-fork-epoch", capellaForkEpoch.toString());
      specConfigModifier =
          specConfigModifier.andThen(
              specConfigBuilder ->
                  specConfigBuilder.capellaBuilder(
                      capellaBuilder -> capellaBuilder.capellaForkEpoch(capellaForkEpoch)));
      return this;
    }

    public Config withTotalTerminalDifficulty(final long totalTerminalDifficulty) {
      return withTotalTerminalDifficulty(UInt256.valueOf(totalTerminalDifficulty));
    }

    public Config withTotalTerminalDifficulty(final UInt256 totalTerminalDifficulty) {
      configMap.put(
          "Xnetwork-total-terminal-difficulty-override",
          totalTerminalDifficulty.toBigInteger().toString());
      specConfigModifier =
          specConfigModifier.andThen(
              specConfigBuilder ->
                  specConfigBuilder.bellatrixBuilder(
                      bellatrixBuilder ->
                          bellatrixBuilder.terminalTotalDifficulty(totalTerminalDifficulty)));
      return this;
    }

    public Config withInitialState(final InitialStateData initialState) {
      configMap.put("initial-state", INITIAL_STATE_FILE);
      this.maybeInitialState = Optional.of(initialState);
      return this;
    }

    public Config withValidatorProposerDefaultFeeRecipient(
        final String validatorProposerDefaultFeeRecipient) {
      configMap.put(
          "validators-proposer-default-fee-recipient", validatorProposerDefaultFeeRecipient);
      return this;
    }

    public Config withExecutionEngine(final BesuNode node) {
      configMap.put("ee-endpoint", node.getInternalEngineJsonRpcUrl());
      return this;
    }

    public Config withStubExecutionEngine() {
      configMap.put("ee-endpoint", "unsafe-test-stub");
      return this;
    }

    public Config withJwtSecretFile(final URL jwtFile) {
      this.maybeJwtFile = Optional.of(jwtFile);
      configMap.put(EE_JWT_SECRET_FILE_KEY, JWT_SECRET_FILE_PATH);
      return this;
    }

    public Config withRealNetwork() {
      configMap.put("p2p-enabled", true);
      return this;
    }

    public Config withPeers(final TekuNode... nodes) {
      final String peers =
          Arrays.stream(nodes).map(TekuNode::getMultiAddr).collect(Collectors.joining(","));
      LOG.debug("Set peers: {}", peers);
      configMap.put("p2p-static-peers", peers);
      return this;
    }

    public Config withSafeSlotsToImportOptimistically(final Integer slots) {
      configMap.put("Xnetwork-safe-slots-to-import-optimistically", slots);
      return this;
    }

    public Config withStartupTargetPeerCount(Integer startupTargetPeerCount) {
      configMap.put("Xstartup-target-peer-count", startupTargetPeerCount);
      return this;
    }

    public String getPeerId() {
      return peerId.toBase58();
    }

    public Consumer<SpecConfigBuilder> getSpecConfigModifier() {
      return specConfigModifier;
    }

    public Map<File, String> write() throws Exception {
      final Map<File, String> configFiles = new HashMap<>();

      final File configFile = File.createTempFile("config", ".yaml");
      configFile.deleteOnExit();
      writeTo(configFile);
      configFiles.put(configFile, CONFIG_FILE_PATH);

      if (maybeNetworkYaml.isPresent()) {
        configFiles.put(copyToTmpFile(maybeNetworkYaml.get()), NETWORK_FILE_PATH);
      }
      if (maybeJwtFile.isPresent()) {
        configFiles.put(copyToTmpFile(maybeJwtFile.get()), JWT_SECRET_FILE_PATH);
      }
      if (maybeInitialState.isPresent()) {
        final InitialStateData initialStateData = maybeInitialState.get();
        final File initialStateFile =
            copyToTmpFile(initialStateData.writeToTempFile().toURI().toURL());
        configFiles.put(initialStateFile, INITIAL_STATE_FILE);
      }

      final File privateKeyFile = File.createTempFile("private-key", ".txt");
      privateKeyFile.deleteOnExit();
      Files.writeString(
          privateKeyFile.toPath(), Bytes.wrap(privateKey.bytes()).toHexString(), UTF_8);
      configFiles.put(privateKeyFile, PRIVATE_KEY_FILE_PATH);
      return configFiles;
    }

    public String getNetworkName() {
      return networkName;
    }

    private void writeTo(final File configFile) throws Exception {
      YAML_MAPPER.writeValue(configFile, configMap);
    }

    private List<File> getTarballsToCopy() {
      return tarballsToCopy;
    }
  }
}
