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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.serialize;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;

public class VoteTrackerSerializerTest {
  private static VoteTracker votes = new DataStructureUtil().randomVoteTracker();
  private static Bytes votesSerialized = serialize(votes);
  private static VoteTrackerSerializer serializer = new VoteTrackerSerializer();

  @Test
  public void serializesConsistentlyToGenericSerializer() {
    assertThat(Bytes.wrap(serializer.serialize(votes))).isEqualTo(votesSerialized);
  }

  @Test
  public void deserializesConsistentlyToGenericSerializer() {
    assertThat(serializer.deserialize(votesSerialized.toArrayUnsafe())).isEqualTo(votes);
  }
}
