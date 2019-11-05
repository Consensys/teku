/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.util.SimpleOffsetSerializer;

class GoodbyeMessageTest {

  private static final Bytes EXPECTED_SSZ = Bytes.fromHexString("0x0100000000000000");
  private static final GoodbyeMessage MESSAGE = new GoodbyeMessage(UnsignedLong.ONE);

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = SimpleOffsetSerializer.serialize(MESSAGE);
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    final GoodbyeMessage result =
        SimpleOffsetSerializer.deserialize(EXPECTED_SSZ, GoodbyeMessage.class);
    assertThat(result).isEqualToComparingFieldByField(MESSAGE);
  }

  @Test
  public void shouldRejectInvalidReasonCode() {
    assertThatThrownBy(() -> new GoodbyeMessage(UnsignedLong.valueOf(15)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldAllowCustomReasonCodes() {
    new GoodbyeMessage(GoodbyeMessage.MIN_CUSTOM_REASON_CODE);
    new GoodbyeMessage(GoodbyeMessage.MIN_CUSTOM_REASON_CODE.plus(UnsignedLong.ONE));
    new GoodbyeMessage(UnsignedLong.MAX_VALUE);
  }
}
