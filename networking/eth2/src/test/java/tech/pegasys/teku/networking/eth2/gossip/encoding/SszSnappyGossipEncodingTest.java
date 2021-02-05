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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.ViewType;

public class SszSnappyGossipEncodingTest {

  protected final GossipEncoding encoding = createEncoding();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  protected GossipEncoding createEncoding() {
    return GossipEncoding.SSZ_SNAPPY;
  }

  private <T extends ViewRead> T decode(GossipEncoding encoding, Bytes data, ViewType<T> valueType)
      throws DecodingException {
    return encoding.decodeMessage(encoding.prepareMessage(data, valueType), valueType);
  }

  @Test
  public void decode_rejectMessageWithHugeUncompressedLengthPriorToDecompression() {
    Bytes hugeLength = ProtobufEncoder.encodeVarInt(Integer.MAX_VALUE);
    final Bytes data =
        Bytes.concatenate(hugeLength, Bytes.fromHexString("000000000000000000000000"));

    assertThatThrownBy(() -> decode(encoding, data, SignedBeaconBlock.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void roundTrip_aggregate() throws DecodingException {
    final SignedAggregateAndProof original = dataStructureUtil.randomSignedAggregateAndProof();

    final Bytes encoded = encoding.encode(original);
    final SignedAggregateAndProof decoded = decode(encoding, encoded, SignedAggregateAndProof.TYPE);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void roundTrip_attestation() throws DecodingException {
    final Attestation original = dataStructureUtil.randomAttestation();

    final Bytes encoded = encoding.encode(original);
    final Attestation decoded = decode(encoding, encoded, Attestation.TYPE);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void roundTrip_block() throws DecodingException {
    final SignedBeaconBlock original = dataStructureUtil.randomSignedBeaconBlock(1);

    final Bytes encoded = encoding.encode(original);
    final SignedBeaconBlock decoded = decode(encoding, encoded, SignedBeaconBlock.TYPE.get());

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  public void decode_emptyValue() {
    assertThatThrownBy(() -> decode(encoding, Bytes.EMPTY, BeaconState.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_invalidData() {
    final Bytes data = Bytes.fromHexString("0xB1AB1A");
    assertThatThrownBy(() -> decode(encoding, data, BeaconState.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_toWrongType() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = encoding.encode(block);
    assertThatThrownBy(() -> decode(encoding, encoded, BeaconState.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_extraDataAppended() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = Bytes.concatenate(encoding.encode(block), Bytes.fromHexString("0x01"));
    assertThatThrownBy(() -> decode(encoding, encoded, BeaconState.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_rejectMessageShorterThanValidLength() {
    assertThatThrownBy(() -> decode(encoding, Bytes.of(1, 2, 3), SignedBeaconBlock.TYPE.get()))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_rejectMessageLongerThanValidLength() {
    assertThatThrownBy(() -> decode(encoding, Bytes.wrap(new byte[512]), StatusMessage.TYPE))
        .isInstanceOf(DecodingException.class);
  }
}
