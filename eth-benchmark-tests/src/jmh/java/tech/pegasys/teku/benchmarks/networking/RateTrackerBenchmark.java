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

package tech.pegasys.teku.benchmarks.networking;

import static tech.pegasys.teku.networking.eth2.P2PConfig.DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.infrastructure.time.SystemTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.peers.ApprovedRequest;
import tech.pegasys.teku.networking.eth2.peers.RateTracker;
import tech.pegasys.teku.networking.eth2.peers.RateTrackerImpl;

@Fork(1)
@State(Scope.Thread)
public class RateTrackerBenchmark {

  @State(Scope.Benchmark)
  public static class ExecutionPlan {
    private final TimeProvider timeProvider = new SystemTimeProvider();
    public RateTracker config;
    public final List<ApprovedRequest> requestList = new ArrayList<>();
    public final Random random = new Random();

    @Param({"true", "false"})
    public boolean isEmpty;

    @Setup(Level.Invocation)
    public void setup() {
      config = new RateTrackerImpl(DEFAULT_PEER_BLOB_SIDECARS_RATE_LIMIT, 60, timeProvider, "org");

      if (!isEmpty) {
        Optional<ApprovedRequest> request = config.approveObjectsRequest(random.nextLong(30));
        request.ifPresent(requestList::add);
        while (request.isPresent()) {
          request = config.approveObjectsRequest(random.nextLong(30));
          request.ifPresent(requestList::add);
        }
      }
    }
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void addToList(final ExecutionPlan plan) {
    plan.config.approveObjectsRequest(plan.random.nextLong(30));
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void availableObjectCount(final ExecutionPlan plan) {
    plan.config.getAvailableObjectCount();
  }

  @Benchmark
  @Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
  public void adjustRequest(final ExecutionPlan plan) {
    if (plan.isEmpty) {
      plan.config.approveObjectsRequest(plan.random.nextLong(30)).ifPresent(plan.requestList::add);
    }
    plan.config.adjustObjectsRequest(plan.requestList.getFirst(), 1);
  }
}
