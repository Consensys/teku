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

package tech.pegasys.teku.benchmarks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator.BatchSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DepositProcessingBenchmark {

  private static final Spec SPEC = TestSpecFactory.createMainnetAltair();
  private static final File BASE_DIR = new File("/Users/aj/Documents/code/teku/tmp/blocks/");
  private static final List<SignedBeaconBlock> BLOCKS;
  private static final BeaconState PRE_STATE;

  static {
    BLOCKS =
        List.of(BASE_DIR.listFiles((dir, name) -> !name.equals("pre.ssz"))).stream()
            .sorted(
                Comparator.comparingInt(
                    file ->
                        Integer.parseInt(file.getName().substring(0, file.getName().indexOf('.')))))
            .map(
                file -> {
                  try {
                    return SPEC.deserializeSignedBeaconBlock(
                        Bytes.wrap(Files.readAllBytes(file.toPath())));
                  } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                  }
                })
            .collect(Collectors.toList());
    try {
      PRE_STATE =
          SPEC.deserializeBeaconState(
              Bytes.wrap(Files.readAllBytes(BASE_DIR.toPath().resolve("pre.ssz"))));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Benchmark
  public void processBlocks(Blackhole bh) throws Exception {
    BeaconState state = processBlocks();
    bh.consume(state);
  }

  private static BeaconState processBlocks() throws StateTransitionException {
    BeaconState state = PRE_STATE;
    for (SignedBeaconBlock block : BLOCKS) {
      final long start = System.nanoTime();
      state =
          SPEC.processBlock(
              state, block, new BatchSignatureVerifier(), OptimisticExecutionPayloadExecutor.NOOP);
      final long end = System.nanoTime();
      final Bytes32 stateRoot = state.hashTreeRoot();
      final long stateHash = System.nanoTime();
      final long timeTaken = TimeUnit.NANOSECONDS.toMillis(end - start);
      final long withHashTimeTaken = TimeUnit.NANOSECONDS.toMillis(end - start);
      System.out.println(
          "Processed slot "
              + block.getSlot()
              + " with "
              + block.getMessage().getBody().getDeposits().size()
              + " in "
              + timeTaken
              + "ms ("
              + withHashTimeTaken
              + "ms with hash)");
    }
    return state;
  }

  public static void main(String[] args) throws StateTransitionException {
    processBlocks();
  }
}
