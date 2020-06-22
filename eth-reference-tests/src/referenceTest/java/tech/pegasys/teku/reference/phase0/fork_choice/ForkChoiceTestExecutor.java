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

package tech.pegasys.teku.reference.phase0.fork_choice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.core.ForkChoiceUtil;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.phase0.TestExecutor;
import tech.pegasys.teku.reference.phase0.fork_choice.implementatations.ForkChoiceProcessor;
import tech.pegasys.teku.reference.phase0.fork_choice.implementatations.OrigForkChoiceProcessor;
import tech.pegasys.teku.reference.phase0.fork_choice.implementatations.StoreBackedForkChoiceProcessor;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor implements TestExecutor {
  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public static ImmutableMap<String, TestExecutor> FORK_CHOICE_TEST_TYPES =
      ImmutableMap.of("fork_choice/integration_tests", new ForkChoiceTestExecutor());

  public static Stream<Arguments> loadForkChoiceTests() {
    Path path =
        Paths.get(
            "src/referenceTest/java/tech/pegasys/teku/reference/phase0/fork_choice/integration_tests/");
    List<File> testFiles = findForkChoiceTestsByPath(path);
    return testFiles.stream().flatMap(file -> parseForkChoiceFile(file.toPath()).stream());
  }

  private static Optional<? extends Arguments> parseForkChoiceFile(Path path) {
    File file = path.toFile();
    try {
      @SuppressWarnings("rawtypes")
      Map content = mapper.readValue(file, Map.class);

      if (content.containsKey("steps")) {
        BeaconStateImpl genesisState =
            resolvePart(BeaconStateImpl.class, file, content.get("genesis"));

        @SuppressWarnings("unchecked")
        List<Object> steps =
            ((List<Map<String, Object>>) content.get("steps"))
                .stream().map(step -> extractTestStep(file, step)).collect(Collectors.toList());
        return Optional.of(Arguments.of(genesisState, steps, file.getName(), true));
      } else {
        return Optional.empty();
      }
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static List<File> findForkChoiceTestsByPath(Path path) {
    try (Stream<Path> paths = Files.walk(path)) {
      return paths
          .filter(p -> Files.isRegularFile(p) && !p.getParent().endsWith("cache"))
          .map(Path::toFile)
          .filter(f -> f.getName().endsWith(".yaml"))
          .collect(Collectors.toList());
    } catch (IOException e) {
      return Collections.emptyList();
    }
  }

  private static Object extractTestStep(File file, Map<String, Object> stepDescription) {
    ForkChoiceTestStep stepKind = getStepKind(stepDescription);
    Object value = stepDescription.get(stepKind.name());

    switch (stepKind) {
      case slot:
        {
          return UnsignedLong.valueOf((Integer) value);
        }
      case block:
        {
          return resolvePart(SignedBeaconBlock.class, file, value);
        }
      case attestation:
        {
          return resolvePart(Attestation.class, file, value);
        }
      case checks:
        {
          return value;
        }
      default:
        throw new IllegalArgumentException("Unsupported step kind " + stepKind);
    }
  }

  private static ForkChoiceTestStep getStepKind(Map<String, Object> ss) {
    return ss.keySet().stream()
        .map(ForkChoiceTestStep::valueOf)
        .collect(Collectors.toList())
        .get(0);
  }

  private static <T> T resolvePart(Class<T> clazz, File testFile, Object value) {
    if (value instanceof String) {
      String path = (String) value;
      if (path.endsWith(".yaml") || path.endsWith(".ssz")) {
        Path partPath = Paths.get(testFile.getParentFile().getParent(), "cache", path);
        try {
          if (path.endsWith(".ssz")) {
            return SimpleOffsetSerializer.deserialize(
                Bytes.wrap(Files.readAllBytes(partPath)), clazz);
          } else {
            return mapper.readValue(partPath.toFile(), clazz);
          }
        } catch (IOException e) {
          throw new IllegalArgumentException("Couldn't resolve " + path + ": " + e.getMessage());
        }
      }
    }
    return clazz.cast(value);
  }

  @Override
  public void runTest(TestDefinition testDefinition) throws Throwable {
    Path testDirectory = testDefinition.getTestDirectory();
    Arguments arguments = parseForkChoiceFile(testDirectory).get();
    Object[] objects = arguments.get();
    BeaconState genesis = (BeaconState) objects[0];
    @SuppressWarnings("unchecked")
    List<Object> steps = (List<Object>) objects[1];
    String testName = (String) objects[2];
    Boolean protoArrayFC = (Boolean) objects[3];
    runForkChoiceTests(genesis, steps, testName, protoArrayFC);
  }

  @ParameterizedTest(name = "{index}. (protoarray={3}) {2}")
  @MethodSource("loadForkChoiceTests")
  void runForkChoiceTests(
      BeaconState genesis, List<Object> steps, String testName, boolean protoArrayFC) {

    EventBus eventBus = new EventBus();
    RecentChainData storageClient = MemoryOnlyRecentChainData.create(eventBus);
    storageClient.initializeFromGenesis(genesis);

    ForkChoiceProcessor forkChoiceProcessor =
        protoArrayFC
            ? new StoreBackedForkChoiceProcessor(storageClient)
            : new OrigForkChoiceProcessor(storageClient);

    @SuppressWarnings("ModifiedButNotUsed")
    List<SignedBeaconBlock> blockBuffer = new ArrayList<>();
    @SuppressWarnings("ModifiedButNotUsed")
    List<Attestation> attestationBuffer = new ArrayList<>();

    for (Object step : steps) {
      blockBuffer.removeIf(forkChoiceProcessor::processBlock);
      attestationBuffer.removeIf(forkChoiceProcessor::processAttestation);
      if (step instanceof UnsignedLong) {
        UpdatableStore.StoreTransaction transaction = storageClient.startStoreTransaction();
        while (ForkChoiceUtil.get_current_slot(transaction).compareTo((UnsignedLong) step) < 0) {
          ForkChoiceUtil.on_tick(transaction, transaction.getTime().plus(UnsignedLong.ONE));
        }
        assertEquals(step, ForkChoiceUtil.get_current_slot(transaction));
        transaction.commit().join();
      } else if (step instanceof SignedBeaconBlock) {
        for (Attestation attestation :
            ((SignedBeaconBlock) step).getMessage().getBody().getAttestations()) {
          attestationBuffer.add(attestation);
        }
        if (!forkChoiceProcessor.processBlock((SignedBeaconBlock) step)) {
          blockBuffer.add((SignedBeaconBlock) step);
        }
      } else if (step instanceof Attestation) {
        if (!forkChoiceProcessor.processAttestation((Attestation) step)) {
          attestationBuffer.add((Attestation) step);
        }
      } else if (step instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) step;
        for (Map.Entry<String, Object> e : checks.entrySet()) {
          String check = e.getKey();
          switch (check) {
            case "block_in_store":
              {
                Bytes32 root = Bytes32.fromHexString((String) e.getValue());
                assertTrue(
                    storageClient.getBlockByRoot(root).isPresent(),
                    "Block is missing from store :" + root);
                break;
              }
            case "block_not_in_store":
              {
                Bytes32 root = Bytes32.fromHexString((String) e.getValue());
                assertTrue(
                    storageClient.getBlockByRoot(root).isEmpty(),
                    "Block should not have been in store :" + root);
                break;
              }
            case "head":
              {
                Bytes32 root = Bytes32.fromHexString((String) e.getValue());
                Bytes32 head = forkChoiceProcessor.processHead();
                assertEquals(
                    root,
                    head,
                    "Head does not match expected head: \n head: "
                        + head
                        + "\n expectedHead: "
                        + root);
                break;
              }
            case "justified_checkpoint_epoch":
              {
                UnsignedLong expected = UnsignedLong.valueOf((Integer) e.getValue());
                UnsignedLong actual = storageClient.getStore().getJustifiedCheckpoint().getEpoch();
                assertEquals(
                    expected,
                    actual,
                    "Justified checkpoint epoch does not match expected: \n actual: "
                        + actual
                        + "\n expected: "
                        + expected);
                break;
              }
            default:
              throw new IllegalArgumentException();
          }
        }
      } else {
        throw new IllegalArgumentException();
      }
    }
  }

  public enum ForkChoiceTestStep {
    slot,
    block,
    attestation,
    checks;
  }
}
