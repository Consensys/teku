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

package tech.pegasys.teku.benchmarks.util.backing;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@State(Scope.Thread)
public class BeaconStateBenchmark {

  private static final BLSPublicKey pubkey = BLSTestUtil.randomPublicKey(0);
  private static final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(0, TestSpecFactory.createMainnetAltair())
          .withPubKeyGenerator(() -> pubkey);
  private static final BeaconState beaconState = dataStructureUtil.randomBeaconState(400_000);

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
      bh.consume(validator.getPubkeyBytes());
      bh.consume(validator.getEffectiveBalance());
      bh.consume(validator.getActivationEpoch());
      bh.consume(validator.getExitEpoch());
      bh.consume(validator.getWithdrawableEpoch());
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void iterateBalances(Blackhole bh) {
    for (UInt64 balance : beaconState.getBalances().asListUnboxed()) {
      bh.consume(balance);
    }
  }

  @Benchmark
  @Warmup(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void updateBalancesAndHash(Blackhole bh) {
    BeaconState stateW =
        beaconState.updated(
            state -> {
              int size = state.getBalances().size();
              UInt64 balance = UInt64.valueOf(777);
              for (int i = 0; i < size; i++) {
                state.getBalances().setElement(i, balance);
              }
            });
    bh.consume(stateW.hashTreeRoot());
  }
}
