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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler.NODE_NOT_READY;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;

class StatusMessageHandlerTest {

  private static final StatusMessage REMOTE_STATUS =
      new StatusMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x11"),
          UInt64.ZERO,
          Bytes32.fromHexStringLenient("0x11"),
          UInt64.ZERO);
  private static final PeerStatus PEER_STATUS = PeerStatus.fromStatusMessage(REMOTE_STATUS);
  private static final StatusMessage LOCAL_STATUS =
      new StatusMessage(
          Bytes4.rightPad(Bytes.of(4)),
          Bytes32.fromHexStringLenient("0x22"),
          UInt64.ZERO,
          Bytes32.fromHexStringLenient("0x22"),
          UInt64.ZERO);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<StatusMessage> callback = mock(ResponseCallback.class);

  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final String protocolId =
      BeaconChainMethodIds.getStatusMethodId(
          1, RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE));
  private final StatusMessageHandler handler = new StatusMessageHandler(statusMessageFactory);

  @BeforeEach
  public void setUp() {
    when(statusMessageFactory.createStatusMessage()).thenReturn(Optional.of(LOCAL_STATUS));
    when(peer.popRequest()).thenReturn(true);
  }

  @Test
  public void shouldReturnLocalStatus() {
    when(statusMessageFactory.createStatusMessage()).thenReturn(Optional.of(LOCAL_STATUS));
    handler.onIncomingMessage(protocolId, peer, REMOTE_STATUS, callback);

    verify(peer).updateStatus(PEER_STATUS);
    verify(callback).respondAndCompleteSuccessfully(LOCAL_STATUS);
    verifyNoMoreInteractions(callback);
  }

  @Test
  public void shouldRespondWithErrorIfStatusUnavailable() {
    when(statusMessageFactory.createStatusMessage()).thenReturn(Optional.empty());
    handler.onIncomingMessage(protocolId, peer, REMOTE_STATUS, callback);

    verify(peer).updateStatus(PEER_STATUS);
    verify(callback).completeWithErrorResponse(NODE_NOT_READY);
  }
}
