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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

class HelloMessageHandlerTest {

  private static final HelloMessage REMOTE_HELLO =
      new HelloMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x11"),
          UnsignedLong.ZERO,
          Bytes32.fromHexStringLenient("0x11"),
          UnsignedLong.ZERO);
  private static final HelloMessage LOCAL_HELLO =
      new HelloMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x22"),
          UnsignedLong.ZERO,
          Bytes32.fromHexStringLenient("0x22"),
          UnsignedLong.ZERO);
  private final HelloMessageFactory helloMessageFactory = mock(HelloMessageFactory.class);
  private final Peer peer = mock(Peer.class);
  private final HelloMessageHandler handler = new HelloMessageHandler(helloMessageFactory);

  @Test
  public void shouldRejectIncomingHelloWhenWeInitiatedConnection() {
    when(peer.isInitiator()).thenReturn(true);
    assertThrows(IllegalStateException.class, () -> handler.onIncomingMessage(peer, REMOTE_HELLO));
  }

  @Test
  public void shouldRegisterHelloMessageWithPeer() {
    handler.onIncomingMessage(peer, REMOTE_HELLO);
    verify(peer).receivedHelloMessage(REMOTE_HELLO);
  }

  @Test
  public void shouldReturnLocalHelloMessage() {
    when(helloMessageFactory.createHelloMessage()).thenReturn(LOCAL_HELLO);
    assertThat(handler.onIncomingMessage(peer, REMOTE_HELLO)).isSameAs(LOCAL_HELLO);
  }
}
