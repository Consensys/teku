/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation.signatures;

import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool.DEFAULT_MAXIMUM_ATTESTATION_COUNT;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil.BlockRewardData;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationForkChecker;
import tech.pegasys.teku.storage.client.RecentChainData;

@Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@State(Scope.Thread)
public class AggregatingAttestationPoolBenchmark {
  private static final Spec SPEC = TestSpecFactory.createMainnetDeneb();

  // pool dump can be created via something similar to
  // https://github.com/tbenr/teku/commit/bd37ec8f5c6ce02edb3e375a1561e1d934b7d191
  // state and actual block can be obtained the usual ways

  // a reference file can be obtained here
  // https://drive.google.com/file/d/139bA7r88riFODZ7S0FpvtO7hmWmdC_XC/view?usp=drive_link
  private static final String STATE_PATH =
      "BeaconStateDeneb_3630479_03664f196162fb81a4406c508674dd1ede09b883d37d0f3d0f076897f68741d2.ssz";

  // a reference file can be obtained here
  // https://drive.google.com/file/d/1I5vXK-x8ZH9wh40wNf1oACXeF_U3to8J/view?usp=drive_link
  private static final String POOL_DUMP_PATH = "attestations_3630479.multi_ssz";

  // a reference file can be obtained here
  // https://drive.google.com/file/d/1PN0OToyNOV0SyjeQaS7oF3J4cKbmy1nX/view?usp=drive_link
  private static final String ACTUAL_BLOCK_PATH =
      "block-3630480-e652bd51c7e4e528fea0728a3ad96f86ceb92e9daa227f315e96a9884ceb187b.ssz";

  private BeaconState state;
  private BeaconState newBlockState;
  private List<ValidatableAttestation> attestations;
  private AggregatingAttestationPool pool;
  private RecentChainData recentChainData;
  private AttestationForkChecker attestationForkChecker;

  @Setup(Level.Trial)
  public void init() throws Exception {

    this.pool =
        new AggregatingAttestationPool(
            SPEC, recentChainData, new NoOpMetricsSystem(), DEFAULT_MAXIMUM_ATTESTATION_COUNT);
    this.recentChainData = mock(RecentChainData.class);

    try (final FileInputStream fileInputStream = new FileInputStream(STATE_PATH)) {
      this.state =
          SPEC.getGenesisSpec()
              .getSchemaDefinitions()
              .getBeaconStateSchema()
              .sszDeserialize(Bytes.wrap(fileInputStream.readAllBytes()));
    }

    this.attestationForkChecker = new AttestationForkChecker(SPEC, state);

    var attestationSchema = SPEC.getGenesisSpec().getSchemaDefinitions().getAttestationSchema();

    try (final Stream<String> attestationLinesStream = Files.lines(Paths.get(POOL_DUMP_PATH))) {
      attestationLinesStream
          .map(line -> attestationSchema.sszDeserialize(Bytes.fromHexString(line)))
          .map(attestation -> ValidatableAttestation.from(SPEC, attestation))
          .forEach(
              attestation -> {
                attestation.saveCommitteeShufflingSeedAndCommitteesSize(state);
                pool.add(attestation);
              });
    }

    this.newBlockState = SPEC.processSlots(state, state.getSlot().increment());

    System.out.println("init done. Pool size: " + pool.getSize());
  }

  @Benchmark
  public void getAttestationsForBlock(final Blackhole bh) {
    var attestationsForBlock = pool.getAttestationsForBlock(newBlockState, attestationForkChecker);
    bh.consume(attestationsForBlock);
  }

  public void printBlockRewardData() throws Exception {
    final BlockRewardCalculatorUtil blockRewardCalculatorUtil = new BlockRewardCalculatorUtil(SPEC);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
    final UInt64 blockSlot = state.getSlot().increment();

    var block =
        dataStructureUtil
            .blockBuilder(blockSlot.longValue())
            .slot(blockSlot)
            .attestations(pool.getAttestationsForBlock(newBlockState, attestationForkChecker))
            .build()
            .getImmediately();

    BlockRewardData blockRewardData = blockRewardCalculatorUtil.getBlockRewardData(block, state);
    System.out.println(
        "Block attestation rewards: "
            + gweiToEth(UInt64.valueOf(blockRewardData.attestations()))
            + " ETH");

    final SignedBeaconBlock actualBlock;
    try (final FileInputStream fileInputStream = new FileInputStream(ACTUAL_BLOCK_PATH)) {
      actualBlock =
          SPEC.getGenesisSpec()
              .getSchemaDefinitions()
              .getSignedBeaconBlockSchema()
              .sszDeserialize(Bytes.wrap(fileInputStream.readAllBytes()));
    }

    blockRewardData = blockRewardCalculatorUtil.getBlockRewardData(actualBlock.getMessage(), state);
    System.out.println(
        "Block attestation rewards: "
            + gweiToEth(UInt64.valueOf(blockRewardData.attestations()))
            + " ETH (actual block)");
  }

  public static void main(String[] args) throws Exception {
    AggregatingAttestationPoolBenchmark benchmark = new AggregatingAttestationPoolBenchmark();
    benchmark.init();
    benchmark.printBlockRewardData();

    var bh =
        new Blackhole(
            "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");

    for (int i = 0; i < 100; i++) {
      benchmark.getAttestationsForBlock(bh);
    }
  }
}
