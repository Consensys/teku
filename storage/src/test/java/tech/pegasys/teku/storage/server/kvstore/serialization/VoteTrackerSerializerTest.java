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
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker.Version;

public class VoteTrackerSerializerTest {

  private static final Bytes32 EXPECTED_CURRENT_ROOT =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final Bytes32 EXPECTED_NEXT_ROOT =
      Bytes32.fromHexString("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");
  private static final UInt64 EXPECTED_NEXT_EPOCH = UInt64.valueOf(4669978815449698508L);

  private static final VoteTracker votesV1 =
      VoteTracker.create(EXPECTED_CURRENT_ROOT, EXPECTED_NEXT_ROOT, EXPECTED_NEXT_EPOCH);
  private static final Bytes votesV1Serialized =
      Bytes.fromHexString(
          "0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41befcc907a73fd18cf40");
  private static final VoteTrackerSerializer serializer = new VoteTrackerSerializer();

  @Test
  public void serializesV1ConsistentlyToGenericSerializer() {
    assertThat(Bytes.wrap(serializer.serialize(votesV1))).isEqualTo(votesV1Serialized);
  }

  @Test
  public void deserializesV1ConsistentlyToGenericSerializer() {
    VoteTracker votes = serializer.deserialize(votesV1Serialized.toArrayUnsafe());
    assertThat(votes).isEqualTo(votesV1);
    assertThat(votes.getVersion()).isEqualTo(Version.V1);
  }

  @Test
  public void serializesDeserializesV2Consistently() {
    VoteTracker votesV2 = VoteTracker.markToEquivocate(votesV1);
    VoteTracker votesV2deserialized = serializer.deserialize(serializer.serialize(votesV2));
    assertThat(votesV2deserialized).isEqualTo(votesV2);
    assertThat(votesV2deserialized.getVersion()).isEqualTo(Version.V2);
  }

  @Test
  public void serializesDeserializesV2EquivocatedConsistently() {
    VoteTracker votesV2 = VoteTracker.createEquivocated(votesV1);
    VoteTracker votesV2deserialized = serializer.deserialize(serializer.serialize(votesV2));
    assertThat(votesV2deserialized).isEqualTo(votesV2);
    assertThat(votesV2deserialized.getVersion()).isEqualTo(Version.V2);
  }
}
