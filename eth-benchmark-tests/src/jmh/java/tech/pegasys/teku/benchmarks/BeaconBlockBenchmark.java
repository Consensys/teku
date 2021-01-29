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

import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.config.Constants;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class BeaconBlockBenchmark {

  private static final BLSPublicKey pubkey = BLSTestUtil.randomPublicKey(0);
  private static final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(0).withPubKeyGenerator(() -> pubkey);
  private static final BeaconBlock fullBeaconBlock =
      dataStructureUtil.randomBeaconBlock(100, Bytes32.random(), true);
  private static final BeaconBlock sparseBeaconBlock =
      dataStructureUtil.randomBeaconBlock(100, Bytes32.random(), false);

  public BeaconBlockBenchmark() {
    Constants.setConstants("mainnet");
  }

  @Benchmark
  public void hashFullBlocks(Blackhole bh) {
    bh.consume(fullBeaconBlock.hash_tree_root());
  }

  @Benchmark
  public void hashSparseBlocks(Blackhole bh) {
    bh.consume(sparseBeaconBlock.hash_tree_root());
  }
}
