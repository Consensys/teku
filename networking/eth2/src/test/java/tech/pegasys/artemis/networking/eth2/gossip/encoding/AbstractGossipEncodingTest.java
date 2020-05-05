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

package tech.pegasys.artemis.networking.eth2.gossip.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;

public abstract class AbstractGossipEncodingTest {

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private final GossipEncoding encoding = createEncoding();

  protected abstract GossipEncoding createEncoding();

  @Test
  public void roundTrip_state() throws DecodingException {
    final BeaconState state = dataStructureUtil.randomBeaconState(1);

    final Bytes encoded = encoding.encode(state);
    final BeaconState decoded = encoding.decode(encoded, BeaconStateImpl.class);

    assertThat(decoded).isEqualTo(state);
  }

  @Test
  public void decode_invalidData() {
    final Bytes data = Bytes.fromHexString("0xB1AB1A");
    assertThatThrownBy(() -> encoding.decode(data, BeaconStateImpl.class))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_toWrongType() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = encoding.encode(block);
    assertThatThrownBy(() -> encoding.decode(encoded, BeaconStateImpl.class))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_extraDataAppended() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = Bytes.concatenate(encoding.encode(block), Bytes.fromHexString("0x01"));
    assertThatThrownBy(() -> encoding.decode(encoded, BeaconStateImpl.class))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_toInvalidClass() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = encoding.encode(block);
    assertThatThrownBy(() -> encoding.decode(encoded, RandomClass.class))
        .isInstanceOf(DecodingException.class);
  }

  @Test
  public void decode_toInvalidPrimitive() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1);

    final Bytes encoded = encoding.encode(block);
    assertThatThrownBy(() -> encoding.decode(encoded, boolean.class))
        .isInstanceOf(DecodingException.class);
  }

  private static class RandomClass {}
}
