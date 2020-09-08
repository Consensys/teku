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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;

public class SszSnappyGossipEncodingTest extends AbstractGossipEncodingTest {

  @Override
  protected GossipEncoding createEncoding() {
    return GossipEncoding.SSZ_SNAPPY;
  }

  @Test
  public void decode_rejectMessageWithHugeUncompressedLengthPriorToDecompression() {
    Bytes hugeLength = ProtobufEncoder.encodeVarInt(Integer.MAX_VALUE);
    final Bytes data =
        Bytes.concatenate(hugeLength, Bytes.fromHexString("000000000000000000000000"));

    assertThatThrownBy(() -> encoding.decode(data, SignedBeaconBlock.class))
        .isInstanceOf(DecodingException.class);
  }
}
