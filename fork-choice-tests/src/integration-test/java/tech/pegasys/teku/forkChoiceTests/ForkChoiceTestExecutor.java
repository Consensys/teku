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

package tech.pegasys.teku.forkChoiceTests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
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
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.executionengine.StubExecutionEngineChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class ForkChoiceTestExecutor {
  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
  private static final Spec SPEC = TestSpecFactory.createMinimalPhase0();

  public static Stream<Arguments> loadForkChoiceTests() {
    Path path = Paths.get("src/integration-test/resources/");
    List<File> testFiles = findForkChoiceTestsByPath(path);
    return testFiles.stream().flatMap(file -> parseForkChoiceFile(file.toPath()).stream());
  }

  @SuppressWarnings("unchecked")
  private static Optional<? extends Arguments> parseForkChoiceFile(Path path) {
    final File file = path.toFile();
    final SchemaDefinitions schemaDefinitions = SPEC.getGenesisSchemaDefinitions();
    final BeaconStateSchema<?, ?> beaconStateSchema = schemaDefinitions.getBeaconStateSchema();
    try {
      @SuppressWarnings("rawtypes")
      Map content = mapper.readValue(file, Map.class);

      if (content.containsKey("steps")) {
        BeaconState genesisState =
            resolvePart(BeaconState.class, beaconStateSchema, file, content.get("genesis"));

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
          return UInt64.valueOf((Integer) value);
        }
      case block:
        {
          return resolvePart(
              SignedBeaconBlock.class,
              SPEC.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema(),
              file,
              value);
        }
      case attestation:
        {
          return resolvePart(Attestation.class, Attestation.SSZ_SCHEMA, file, value);
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

  private static <T extends SszData> T resolvePart(
      Class<T> clazz, SszSchema<? extends T> type, File testFile, Object value) {
    if (value instanceof String) {
      String path = (String) value;
      if (path.endsWith(".yaml") || path.endsWith(".ssz")) {
        Path partPath = Paths.get(testFile.getParentFile().getParent(), "cache", path);
        try {
          if (path.endsWith(".ssz")) {
            return type.sszDeserialize(Bytes.wrap(Files.readAllBytes(partPath)));
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

  @ParameterizedTest(name = "{index}.{2} fork choice test")
  @MethodSource("loadForkChoiceTests")
  void runForkChoiceTests(
      BeaconState genesis, List<Object> steps, String testName, boolean protoArrayFC) {

    RecentChainData storageClient = MemoryOnlyRecentChainData.create(SPEC);
    storageClient.initializeFromGenesis(genesis, UInt64.ZERO);

    final InlineEventThread forkChoiceExecutor = new InlineEventThread();
    ForkChoice forkChoice =
        ForkChoice.create(SPEC, forkChoiceExecutor, storageClient, mock(ForkChoiceNotifier.class));

    @SuppressWarnings("ModifiedButNotUsed")
    List<SignedBeaconBlock> blockBuffer = new ArrayList<>();
    @SuppressWarnings("ModifiedButNotUsed")
    List<Attestation> attestationBuffer = new ArrayList<>();

    for (Object step : steps) {
      blockBuffer.removeIf(block -> processBlock(forkChoice, block));
      attestationBuffer.removeIf(attestation -> processAttestation(forkChoice, attestation));
      if (step instanceof UInt64) {
        UpdatableStore.StoreTransaction transaction = storageClient.startStoreTransaction();
        while (SPEC.getCurrentSlot(transaction).compareTo((UInt64) step) < 0) {
          SPEC.onTick(transaction, transaction.getTime().plus(UInt64.ONE));
        }
        assertEquals(step, SPEC.getCurrentSlot(transaction));
        transaction.commit().join();
      } else if (step instanceof SignedBeaconBlock) {
        for (Attestation attestation :
            ((SignedBeaconBlock) step).getMessage().getBody().getAttestations()) {
          attestationBuffer.add(attestation);
        }
        if (!processBlock(forkChoice, (SignedBeaconBlock) step)) {
          blockBuffer.add((SignedBeaconBlock) step);
        }
      } else if (step instanceof Attestation) {
        if (!processAttestation(forkChoice, (Attestation) step)) {
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
                    storageClient.retrieveBlockByRoot(root).join().isPresent(),
                    "Block is missing from store :" + root);
                break;
              }
            case "block_not_in_store":
              {
                Bytes32 root = Bytes32.fromHexString((String) e.getValue());
                assertTrue(
                    storageClient.retrieveBlockByRoot(root).join().isEmpty(),
                    "Block should not have been in store :" + root);
                break;
              }
            case "head":
              {
                Bytes32 root = Bytes32.fromHexString((String) e.getValue());
                forkChoice.processHead().join();
                Bytes32 head = storageClient.getBestBlockRoot().orElseThrow();
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
                UInt64 expected = UInt64.valueOf((Integer) e.getValue());
                UInt64 actual = storageClient.getStore().getJustifiedCheckpoint().getEpoch();
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

  private boolean processAttestation(ForkChoice fc, Attestation step) {
    AttestationProcessingResult attestationProcessingResult =
        fc.onAttestation(ValidateableAttestation.from(SPEC, step)).join();
    return attestationProcessingResult.isSuccessful();
  }

  private boolean processBlock(ForkChoice fc, SignedBeaconBlock block) {
    BlockImportResult blockImportResult =
        fc.onBlock(block, new StubExecutionEngineChannel(SPEC)).join();
    return blockImportResult.isSuccessful();
  }

  public enum ForkChoiceTestStep {
    slot,
    block,
    attestation,
    checks
  }
}
