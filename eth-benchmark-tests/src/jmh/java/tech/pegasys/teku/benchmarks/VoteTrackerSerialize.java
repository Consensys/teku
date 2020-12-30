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

import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.deserialize;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.serialize;

import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

@Fork(5)
public class VoteTrackerSerialize {

  private static VoteTracker votes = new DataStructureUtil().randomVoteTracker();
  private static Bytes votesSerialized = serialize(votes);

  @Benchmark
  @Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void VoteTrackerSerialization() {
    serialize(votes);
  }

  @Benchmark
  @Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
  public void VoteTrackerDeserialization() {
    deserialize(votesSerialized, VoteTracker.class);
  }
}
