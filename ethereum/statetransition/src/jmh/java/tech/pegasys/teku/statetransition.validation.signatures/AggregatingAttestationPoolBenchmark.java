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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.StateTransition;
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
  private static final String STATE_PATH =
      "/Users/tbenr/Downloads/att pool test/BeaconStateDeneb_3630479_03664f196162fb81a4406c508674dd1ede09b883d37d0f3d0f076897f68741d2.ssz";
  private static final String POOL_DUMP_PATH =
      "/Users/tbenr/Downloads/att pool test/attestations_3630479.multi_ssz";

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

    StateTransition stateTransition = new StateTransition(SPEC::atSlot);

    this.newBlockState = stateTransition.processSlots(state, state.getSlot().increment());

    System.out.println("init done. Pool size: " + pool.getSize());
  }

  @Benchmark
  public void getAttestationsForBlock(final Blackhole bh) {
    var attestationsForBlock = pool.getAttestationsForBlock(newBlockState, attestationForkChecker);
    bh.consume(attestationsForBlock);
  }

  public static void main(String[] args) throws Exception {
    AggregatingAttestationPoolBenchmark benchmark = new AggregatingAttestationPoolBenchmark();
    benchmark.init();
    benchmark.getAttestationsForBlock(
        new Blackhole(
            "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous."));
  }
}
