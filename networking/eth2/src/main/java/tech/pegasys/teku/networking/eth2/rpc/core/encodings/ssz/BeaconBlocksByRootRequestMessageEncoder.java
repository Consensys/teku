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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz;

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.datastructures.util.LengthBounds;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;

public class BeaconBlocksByRootRequestMessageEncoder
    implements RpcPayloadEncoder<BeaconBlocksByRootRequestMessage> {
  private static final Logger LOG = LogManager.getLogger();

  private static final LengthBounds VALID_LENGTH_BOUNDS =
      new LengthBounds(Bytes32.SIZE, MAX_REQUEST_BLOCKS * Bytes32.SIZE);

  @Override
  public Bytes encode(final BeaconBlocksByRootRequestMessage message) {
    return SSZ.encode(writer -> writer.writeFixedBytesVector(message.getBlockRoots().asList()));
  }

  @Override
  public BeaconBlocksByRootRequestMessage decode(final Bytes message) throws RpcException {
    if (message.size() % Bytes32.SIZE != 0) {
      LOG.trace("Cannot split message into Bytes32 chunks {}", message);
      throw new DeserializationFailedException();
    }
    final List<Bytes32> blockRoots = new ArrayList<>();
    for (int i = 0; i < message.size(); i += Bytes32.SIZE) {
      blockRoots.add(Bytes32.wrap(message.slice(i, Bytes32.SIZE)));
    }
    if (blockRoots.size() > MAX_REQUEST_BLOCKS) {
      throw new RpcException(
          INVALID_REQUEST_CODE, "Only a maximum of " + MAX_REQUEST_BLOCKS + " can per request");
    }

    return new BeaconBlocksByRootRequestMessage(blockRoots);
  }

  @Override
  public boolean isLengthWithinBounds(final long length) {
    return VALID_LENGTH_BOUNDS.isWithinBounds(length);
  }
}
