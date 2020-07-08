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

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import static tech.pegasys.teku.networking.p2p.DiscoveryNetwork.ATTESTATION_SUBNET_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.DiscoveryNetwork.ETH2_ENR_FIELD;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;

public class NodeRecordConverter {
  private static final Logger LOG = LogManager.getLogger();

  static Optional<DiscoveryPeer> convertToDiscoveryPeer(final NodeRecord nodeRecord) {
    return nodeRecord
        .getTcpAddress()
        .map(address -> socketAddressToDiscoveryPeer(nodeRecord, address));
  }

  private static DiscoveryPeer socketAddressToDiscoveryPeer(
      final NodeRecord nodeRecord, final InetSocketAddress address) {

    final Optional<EnrForkId> enrForkId =
        parseField(
            nodeRecord,
            ETH2_ENR_FIELD,
            enrField -> SimpleOffsetSerializer.deserialize(enrField, EnrForkId.class));

    final Bitvector persistentSubnets =
        parseField(
                nodeRecord,
                ATTESTATION_SUBNET_ENR_FIELD,
                attestionSubnetsField ->
                    Bitvector.fromBytes(attestionSubnetsField, ATTESTATION_SUBNET_COUNT))
            .orElse(new Bitvector(ATTESTATION_SUBNET_COUNT));

    return new DiscoveryPeer(
        ((Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1)), address, enrForkId, persistentSubnets);
  }

  private static <T> Optional<T> parseField(
      final NodeRecord nodeRecord, final String fieldName, final Function<Bytes, T> parse) {
    try {
      return Optional.ofNullable((Bytes) nodeRecord.get(fieldName)).map(parse);
    } catch (final Exception e) {
      LOG.debug("Failed to parse ENR field {}", fieldName, e);
      return Optional.empty();
    }
  }
}
