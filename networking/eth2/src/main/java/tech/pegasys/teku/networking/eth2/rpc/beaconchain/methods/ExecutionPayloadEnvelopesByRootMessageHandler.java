/*
 * Copyright Consensys Software Inc., 2022
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

import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;

// TODO-GLOAS: https://github.com/Consensys/teku/issues/9974
/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/p2p-interface.md#executionpayloadenvelopesbyroot-v1">ExecutionPayloadEnvelopesByRoot</a>
 */
public class ExecutionPayloadEnvelopesByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<
        ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope> {

  public ExecutionPayloadEnvelopesByRootMessageHandler() {}

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final ExecutionPayloadEnvelopesByRootRequestMessage message,
      final ResponseCallback<SignedExecutionPayloadEnvelope> responseCallback) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
