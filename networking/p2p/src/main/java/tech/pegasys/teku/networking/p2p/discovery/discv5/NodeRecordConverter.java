/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.ATTESTATION_SUBNET_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.DAS_CUSTODY_GROUP_COUNT_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.ETH2_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.NEXT_FORK_DIGEST_ENR_FIELD;
import static tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.SYNC_COMMITTEE_SUBNET_ENR_FIELD;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class NodeRecordConverter {
  private static final Logger LOG = LogManager.getLogger();

  public Bytes convertPublicKeyToNodeId(final Bytes publicKey) {
    return IdentitySchemaInterpreter.V4.calculateNodeId(publicKey);
  }

  public Optional<DiscoveryPeer> convertToDiscoveryPeer(
      final NodeRecord nodeRecord,
      final boolean supportsIpv6,
      final SchemaDefinitions schemaDefinitions) {
    final Optional<InetSocketAddress> tcpAddress;
    if (supportsIpv6) {
      // prefer IPv6 address
      tcpAddress = nodeRecord.getTcp6Address().or(nodeRecord::getTcpAddress);
    } else {
      tcpAddress = nodeRecord.getTcpAddress();
    }
    return tcpAddress.map(
        address -> socketAddressToDiscoveryPeer(schemaDefinitions, nodeRecord, address));
  }

  private static DiscoveryPeer socketAddressToDiscoveryPeer(
      final SchemaDefinitions schemaDefinitions,
      final NodeRecord nodeRecord,
      final InetSocketAddress address) {

    final Optional<EnrForkId> enrForkId =
        parseField(nodeRecord, ETH2_ENR_FIELD, EnrForkId.SSZ_SCHEMA::sszDeserialize);

    final SszBitvectorSchema<SszBitvector> attnetsSchema =
        schemaDefinitions.getAttnetsENRFieldSchema();
    final SszBitvector persistentAttestationSubnets =
        parseField(nodeRecord, ATTESTATION_SUBNET_ENR_FIELD, attnetsSchema::fromBytes)
            .orElse(attnetsSchema.getDefault());

    final SszBitvectorSchema<SszBitvector> syncnetsSchema =
        schemaDefinitions.getSyncnetsENRFieldSchema();
    final SszBitvector syncCommitteeSubnets =
        parseField(nodeRecord, SYNC_COMMITTEE_SUBNET_ENR_FIELD, syncnetsSchema::fromBytes)
            .orElse(syncnetsSchema.getDefault());
    final Optional<Integer> dasTotalCustodySubnetCount =
        parseField(
            nodeRecord,
            DAS_CUSTODY_GROUP_COUNT_ENR_FIELD,
            bytes -> UInt64.fromBytes(bytes).intValue());
    final Optional<Bytes4> nextForkDigest =
        parseField(
                nodeRecord,
                NEXT_FORK_DIGEST_ENR_FIELD,
                SszPrimitiveSchemas.BYTES4_SCHEMA::sszDeserialize)
            .map(SszBytes4::get);

    return new DiscoveryPeer(
        ((Bytes) nodeRecord.get(EnrField.PKEY_SECP256K1)),
        nodeRecord.getNodeId(),
        address,
        enrForkId,
        persistentAttestationSubnets,
        syncCommitteeSubnets,
        dasTotalCustodySubnetCount,
        nextForkDigest);
  }

  private static <T> Optional<T> parseField(
      final NodeRecord nodeRecord, final String fieldName, final Function<Bytes, T> parse) {
    try {
      return Optional.ofNullable((Bytes) nodeRecord.get(fieldName)).map(parse);
    } catch (final Exception e) {
      LOG.debug("Failed to parse ENR field {}: {}", fieldName, e);
      return Optional.empty();
    }
  }
}
