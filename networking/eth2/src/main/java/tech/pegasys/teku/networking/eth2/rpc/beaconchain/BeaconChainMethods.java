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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static tech.pegasys.teku.spec.config.Constants.MAX_BLOCK_BY_RANGE_REQUEST_SIZE;
import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOBS_SIDECARS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlockAndBlobsSidecarByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobsSidecarsByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.GoodbyeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.PingMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.ForkDigestPayloadContext;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.SingleProtocolEth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.VersionedEth2RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage.BeaconBlockAndBlobsSidecarByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage.BeaconBlocksByRangeRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobsSidecarsByRangeRequestMessage.BlobsSidecarsByRangeRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage.EmptyMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconChainMethods {

  private final Eth2RpcMethod<StatusMessage, StatusMessage> status;
  private final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye;
  private final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      beaconBlocksByRoot;
  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      beaconBlocksByRange;
  private final Optional<
          Eth2RpcMethod<
              BeaconBlockAndBlobsSidecarByRootRequestMessage, SignedBeaconBlockAndBlobsSidecar>>
      beaconBlockAndBlobsSidecarByRoot;
  private final Optional<Eth2RpcMethod<BlobsSidecarsByRangeRequestMessage, BlobsSidecar>>
      blobsSidecarsByRange;
  private final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata;
  private final Eth2RpcMethod<PingMessage, PingMessage> ping;

  private final Collection<RpcMethod<?, ?, ?>> allMethods;

  private BeaconChainMethods(
      final Eth2RpcMethod<StatusMessage, StatusMessage> status,
      final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye,
      final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot,
      final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange,
      final Optional<
              Eth2RpcMethod<
                  BeaconBlockAndBlobsSidecarByRootRequestMessage, SignedBeaconBlockAndBlobsSidecar>>
          beaconBlockAndBlobsSidecarByRoot,
      final Optional<Eth2RpcMethod<BlobsSidecarsByRangeRequestMessage, BlobsSidecar>>
          blobsSidecarsByRange,
      final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata,
      final Eth2RpcMethod<PingMessage, PingMessage> ping) {
    this.status = status;
    this.goodBye = goodBye;
    this.beaconBlocksByRoot = beaconBlocksByRoot;
    this.beaconBlocksByRange = beaconBlocksByRange;
    this.beaconBlockAndBlobsSidecarByRoot = beaconBlockAndBlobsSidecarByRoot;
    this.blobsSidecarsByRange = blobsSidecarsByRange;
    this.getMetadata = getMetadata;
    this.ping = ping;
    this.allMethods =
        new ArrayList<>(
            List.of(status, goodBye, beaconBlocksByRoot, beaconBlocksByRange, getMetadata, ping));
    beaconBlockAndBlobsSidecarByRoot().ifPresent(allMethods::add);
    blobsSidecarsByRange.ifPresent(allMethods::add);
  }

  public static BeaconChainMethods create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final RpcEncoding rpcEncoding) {
    return new BeaconChainMethods(
        createStatus(asyncRunner, statusMessageFactory, peerLookup, rpcEncoding),
        createGoodBye(asyncRunner, metricsSystem, peerLookup, rpcEncoding),
        createBeaconBlocksByRoot(
            spec, metricsSystem, asyncRunner, recentChainData, peerLookup, rpcEncoding),
        createBeaconBlocksByRange(
            spec,
            metricsSystem,
            asyncRunner,
            recentChainData,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding),
        createBeaconBlockAndBlobsSidecarByRoot(
            spec, metricsSystem, asyncRunner, recentChainData, peerLookup, rpcEncoding),
        createBlobsSidecarsByRange(
            spec,
            metricsSystem,
            asyncRunner,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding,
            recentChainData),
        createMetadata(spec, asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding),
        createPing(asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding));
  }

  private static Eth2RpcMethod<StatusMessage, StatusMessage> createStatus(
      final AsyncRunner asyncRunner,
      final StatusMessageFactory statusMessageFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final StatusMessageHandler statusHandler = new StatusMessageHandler(statusMessageFactory);
    final RpcContextCodec<?, StatusMessage> contextCodec =
        RpcContextCodec.noop(StatusMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BeaconChainMethodIds.STATUS,
        1,
        rpcEncoding,
        StatusMessage.SSZ_SCHEMA,
        true,
        contextCodec,
        statusHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> createGoodBye(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    final RpcContextCodec<?, GoodbyeMessage> contextCodec =
        RpcContextCodec.noop(GoodbyeMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BeaconChainMethodIds.GOODBYE,
        1,
        rpcEncoding,
        GoodbyeMessage.SSZ_SCHEMA,
        false,
        contextCodec,
        goodbyeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRoot(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(spec, metricsSystem, recentChainData);

    final BeaconBlocksByRootRequestMessageSchema requestType =
        BeaconBlocksByRootRequestMessage.SSZ_SCHEMA;
    final boolean expectResponseToRequest = true;

    // V1 request only deal with Phase0 blocks
    final SignedBeaconBlockSchema phase0BlockSchema =
        spec.forMilestone(SpecMilestone.PHASE0).getSchemaDefinitions().getSignedBeaconBlockSchema();
    final RpcContextCodec<Bytes, SignedBeaconBlock> noContextCodec =
        RpcContextCodec.noop(phase0BlockSchema);

    final SingleProtocolEth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
        v1Method =
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                BeaconChainMethodIds.BEACON_BLOCKS_BY_ROOT,
                1,
                rpcEncoding,
                requestType,
                expectResponseToRequest,
                noContextCodec,
                beaconBlocksByRootHandler,
                peerLookup);

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final RpcContextCodec<Bytes4, SignedBeaconBlock> forkDigestContextCodec =
          RpcContextCodec.forkDigest(
              spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK);

      final SingleProtocolEth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
          v2Method =
              new SingleProtocolEth2RpcMethod<>(
                  asyncRunner,
                  BeaconChainMethodIds.BEACON_BLOCKS_BY_ROOT,
                  2,
                  rpcEncoding,
                  requestType,
                  expectResponseToRequest,
                  forkDigestContextCodec,
                  beaconBlocksByRootHandler,
                  peerLookup);

      return VersionedEth2RpcMethod.create(
          rpcEncoding, requestType, expectResponseToRequest, List.of(v2Method, v1Method));
    } else {
      return v1Method;
    }
  }

  private static Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRange(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {

    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(
            spec, metricsSystem, combinedChainDataClient, MAX_BLOCK_BY_RANGE_REQUEST_SIZE);
    // V1 request only deal with Phase0 blocks
    final SignedBeaconBlockSchema phase0BlockSchema =
        spec.forMilestone(SpecMilestone.PHASE0).getSchemaDefinitions().getSignedBeaconBlockSchema();
    final RpcContextCodec<?, SignedBeaconBlock> noContextCodec =
        RpcContextCodec.noop(phase0BlockSchema);

    final BeaconBlocksByRangeRequestMessageSchema requestType =
        BeaconBlocksByRangeRequestMessage.SSZ_SCHEMA;
    final boolean expectResponseToRequest = true;

    final SingleProtocolEth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
        v1Method =
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE,
                1,
                rpcEncoding,
                requestType,
                expectResponseToRequest,
                noContextCodec,
                beaconBlocksByRangeHandler,
                peerLookup);

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final RpcContextCodec<Bytes4, SignedBeaconBlock> forkDigestContextCodec =
          RpcContextCodec.forkDigest(
              spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK);

      final SingleProtocolEth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
          v2Method =
              new SingleProtocolEth2RpcMethod<>(
                  asyncRunner,
                  BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE,
                  2,
                  rpcEncoding,
                  requestType,
                  expectResponseToRequest,
                  forkDigestContextCodec,
                  beaconBlocksByRangeHandler,
                  peerLookup);

      return VersionedEth2RpcMethod.create(
          rpcEncoding, requestType, expectResponseToRequest, List.of(v2Method, v1Method));
    } else {
      return v1Method;
    }
  }

  private static Optional<
          Eth2RpcMethod<
              BeaconBlockAndBlobsSidecarByRootRequestMessage, SignedBeaconBlockAndBlobsSidecar>>
      createBeaconBlockAndBlobsSidecarByRoot(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    if (!spec.isMilestoneSupported(SpecMilestone.EIP4844)) {
      return Optional.empty();
    }

    final BeaconBlockAndBlobsSidecarByRootRequestMessageSchema requestType =
        BeaconBlockAndBlobsSidecarByRootRequestMessage.SSZ_SCHEMA;

    final RpcContextCodec<Bytes4, SignedBeaconBlockAndBlobsSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK_AND_BLOBS_SIDECAR);

    final BeaconBlockAndBlobsSidecarByRootMessageHandler messageHandler =
        new BeaconBlockAndBlobsSidecarByRootMessageHandler(
            spec, getEip4844ForkEpoch(spec), metricsSystem, recentChainData);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.BEACON_BLOCK_AND_BLOBS_SIDECAR_BY_ROOT,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            messageHandler,
            peerLookup));
  }

  private static Optional<Eth2RpcMethod<BlobsSidecarsByRangeRequestMessage, BlobsSidecar>>
      createBlobsSidecarsByRange(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData) {

    if (!spec.isMilestoneSupported(SpecMilestone.EIP4844)) {
      return Optional.empty();
    }

    final BlobsSidecarsByRangeRequestMessageSchema requestType =
        BlobsSidecarsByRangeRequestMessage.SSZ_SCHEMA;

    final RpcContextCodec<Bytes4, BlobsSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(spec, recentChainData, ForkDigestPayloadContext.BLOBS_SIDECAR);

    final BlobsSidecarsByRangeMessageHandler blobsSidecarsByRangeHandler =
        new BlobsSidecarsByRangeMessageHandler(
            spec,
            getEip4844ForkEpoch(spec),
            metricsSystem,
            combinedChainDataClient,
            MAX_REQUEST_BLOBS_SIDECARS);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.BLOBS_SIDECARS_BY_RANGE,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            blobsSidecarsByRangeHandler,
            peerLookup));
  }

  private static Eth2RpcMethod<EmptyMessage, MetadataMessage> createMetadata(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final MetadataMessageHandler messageHandler =
        new MetadataMessageHandler(spec, metadataMessagesFactory);
    final EmptyMessageSchema requestType = EmptyMessage.SSZ_SCHEMA;
    final boolean expectResponse = true;
    final SszSchema<MetadataMessage> phase0MetadataSchema =
        SszSchema.as(
            MetadataMessage.class,
            spec.forMilestone(SpecMilestone.PHASE0)
                .getSchemaDefinitions()
                .getMetadataMessageSchema());
    final RpcContextCodec<?, MetadataMessage> phase0ContextCodec =
        RpcContextCodec.noop(phase0MetadataSchema);

    final SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage> v1Method =
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.GET_METADATA,
            1,
            rpcEncoding,
            requestType,
            expectResponse,
            phase0ContextCodec,
            messageHandler,
            peerLookup);

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final SszSchema<MetadataMessage> altairMetadataSchema =
          SszSchema.as(
              MetadataMessage.class,
              spec.forMilestone(SpecMilestone.ALTAIR)
                  .getSchemaDefinitions()
                  .getMetadataMessageSchema());
      final RpcContextCodec<?, MetadataMessage> altairContextCodec =
          RpcContextCodec.noop(altairMetadataSchema);

      final SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage> v2Method =
          new SingleProtocolEth2RpcMethod<>(
              asyncRunner,
              BeaconChainMethodIds.GET_METADATA,
              2,
              rpcEncoding,
              requestType,
              expectResponse,
              altairContextCodec,
              messageHandler,
              peerLookup);
      return VersionedEth2RpcMethod.create(
          rpcEncoding, requestType, expectResponse, List.of(v2Method, v1Method));
    } else {
      return v1Method;
    }
  }

  private static Eth2RpcMethod<PingMessage, PingMessage> createPing(
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final PingMessageHandler statusHandler = new PingMessageHandler(metadataMessagesFactory);
    final RpcContextCodec<?, PingMessage> contextCodec =
        RpcContextCodec.noop(PingMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BeaconChainMethodIds.PING,
        1,
        rpcEncoding,
        PingMessage.SSZ_SCHEMA,
        true,
        contextCodec,
        statusHandler,
        peerLookup);
  }

  private static UInt64 getEip4844ForkEpoch(final Spec spec) {
    return spec.forMilestone(SpecMilestone.EIP4844)
        .getConfig()
        .toVersionEip4844()
        .orElseThrow()
        .getEip4844ForkEpoch();
  }

  public Collection<RpcMethod<?, ?, ?>> all() {
    return Collections.unmodifiableCollection(allMethods);
  }

  public Eth2RpcMethod<StatusMessage, StatusMessage> status() {
    return status;
  }

  public Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye() {
    return goodBye;
  }

  public Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot() {
    return beaconBlocksByRoot;
  }

  public Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange() {
    return beaconBlocksByRange;
  }

  public Optional<
          Eth2RpcMethod<
              BeaconBlockAndBlobsSidecarByRootRequestMessage, SignedBeaconBlockAndBlobsSidecar>>
      beaconBlockAndBlobsSidecarByRoot() {
    return beaconBlockAndBlobsSidecarByRoot;
  }

  public Optional<Eth2RpcMethod<BlobsSidecarsByRangeRequestMessage, BlobsSidecar>>
      blobsSidecarsByRange() {
    return blobsSidecarsByRange;
  }

  public Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata() {
    return getMetadata;
  }

  public Eth2RpcMethod<PingMessage, PingMessage> ping() {
    return ping;
  }
}
