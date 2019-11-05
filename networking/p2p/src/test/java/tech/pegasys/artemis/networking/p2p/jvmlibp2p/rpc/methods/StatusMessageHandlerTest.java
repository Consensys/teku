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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.Peer;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.ResponseCallback;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;

class StatusMessageHandlerTest {

  private static final StatusMessage REMOTE_STATUS =
      new StatusMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x11"),
          UnsignedLong.ZERO,
          Bytes32.fromHexStringLenient("0x11"),
          UnsignedLong.ZERO);
  private static final StatusMessage LOCAL_STATUS =
      new StatusMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x22"),
          UnsignedLong.ZERO,
          Bytes32.fromHexStringLenient("0x22"),
          UnsignedLong.ZERO);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<StatusMessage> callback = mock(ResponseCallback.class);

  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final Peer peer = mock(Peer.class);

  private final StatusMessageHandler handler = new StatusMessageHandler(statusMessageFactory);

  @BeforeEach
  public void setUp() {
    when(statusMessageFactory.createStatusMessage()).thenReturn(LOCAL_STATUS);
  }

  @Test
  public void shouldRegisterStatusMessageWithPeer() {
    handler.onIncomingMessage(peer, REMOTE_STATUS, callback);
    verify(peer).updateStatus(REMOTE_STATUS);
  }

  @Test
  public void shouldReturnLocalStatusMessage() {
    handler.onIncomingMessage(peer, REMOTE_STATUS, callback);
    verify(callback).respond(LOCAL_STATUS);
    verify(callback).completeSuccessfully();
    verifyNoMoreInteractions(callback);
  }
}
