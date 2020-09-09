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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.concurrent.atomic.AtomicLong;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.util.config.Constants;

public class MetadataMessagesFactory implements ValueObserver<Iterable<Integer>> {

  private final AtomicLong seqNumberGenerator = new AtomicLong();
  private volatile MetadataMessage currentMessage = MetadataMessage.createDefault();

  @Override
  public void onValueChanged(Iterable<Integer> subnetIds) {
    currentMessage =
        new MetadataMessage(
            UInt64.valueOf(seqNumberGenerator.incrementAndGet()),
            new Bitvector(subnetIds, Constants.ATTESTATION_SUBNET_COUNT));
  }

  public MetadataMessage createMetadataMessage() {
    return currentMessage;
  }

  public PingMessage createPingMessage() {
    return new PingMessage(currentMessage.getSeqNumber());
  }
}
