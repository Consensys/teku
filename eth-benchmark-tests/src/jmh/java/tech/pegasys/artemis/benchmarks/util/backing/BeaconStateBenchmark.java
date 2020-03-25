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

package tech.pegasys.artemis.benchmarks.util.backing;

import com.google.common.primitives.UnsignedLong;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.util.config.Constants;

@Fork(0)
@State(Scope.Thread)
public class BeaconStateBenchmark {

  private static DataStructureUtil dataStructureUtil = new DataStructureUtil(0);
  private static BeaconState beaconState =
      dataStructureUtil.randomBeaconState(32 * 1024).createWritableCopy();
  //  private static MutableBeaconState mutableBeaconState = beaconState.createWritableCopy();

  public BeaconStateBenchmark() {
    Constants.setConstants("mainnet");
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateValidators(Blackhole bh) {
    for (Validator validator : beaconState.getValidators()) {
      bh.consume(validator);
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateValidatorsWithMethods(Blackhole bh) {
    for (Validator validator : beaconState.getValidators()) {
      bh.consume(validator.isSlashed());
      //      bh.consume(validator.getPubkey());
      bh.consume(validator.getEffective_balance());
      bh.consume(validator.getActivation_epoch());
      bh.consume(validator.getExit_epoch());
      bh.consume(validator.getWithdrawable_epoch());
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateBalances(Blackhole bh) {
    for (UnsignedLong balance : beaconState.getBalances()) {
      bh.consume(balance);
    }
  }
}
