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

package tech.pegasys.teku.benchmarks;

import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.VOTE_TRACKER_SERIALIZER;

import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class VoteTrackerSerialize {

  private static Spec spec = TestSpecFactory.createDefault();
  private static VoteTracker votes = new DataStructureUtil(spec).randomVoteTracker();
  private static Bytes votesSerialized = Bytes.wrap(VOTE_TRACKER_SERIALIZER.serialize(votes));

  @Benchmark
  @Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void voteTrackerSerialization() {
    checkSize(Bytes.wrap(VOTE_TRACKER_SERIALIZER.serialize(votes)));
  }

  @Benchmark
  @Warmup(iterations = 2, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 5, time = 100, timeUnit = TimeUnit.MILLISECONDS)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  public void voteTrackerDeserialization() {
    checkEpoch(VOTE_TRACKER_SERIALIZER.deserialize(votesSerialized.toArrayUnsafe()));
  }

  private boolean checkSize(final Bytes serialize) {
    return serialize.size() == votesSerialized.size();
  }

  private boolean checkEpoch(final VoteTracker voteTracker) {
    return votes.getNextEpoch().equals(voteTracker.getNextEpoch());
  }
}
