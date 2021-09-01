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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

public class VoteTrackerSerializerTest {

  private static final Bytes32 expectedCurrentRoot =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final Bytes32 expectedNextRoot =
      Bytes32.fromHexString("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");
  private static final UInt64 expectedNextEpoch = UInt64.valueOf(4669978815449698508L);

  private static VoteTracker votes =
      new VoteTracker(expectedCurrentRoot, expectedNextRoot, expectedNextEpoch);
  private static Bytes votesSerialized =
      Bytes.fromHexString(
          "0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41befcc907a73fd18cf40");
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
