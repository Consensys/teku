package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlockAndBlobsSidecarByRootRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BeaconBlockAndBlobsSidecarByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE);

  private final UInt64 eip4844ForkEpoch = UInt64.ONE;

  private final UInt64 finalizedEpoch = UInt64.valueOf(3);

  private final Spec spec = TestSpecFactory.createMinimalEip4844();

  private final int slotsPerEpoch = spec.getSlotsPerEpoch(UInt64.ONE);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);

  private final String protocolId =
      BeaconChainMethodIds.getBeaconBlockAndBlobsSidecarByRoot(1, RPC_ENCODING);

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final Eth2Peer peer = mock(Eth2Peer.class);

  private final UpdatableStore store = mock(UpdatableStore.class);

  @BeforeEach
  public void setUp() {
    when(recentChainData.getStore()).thenReturn(store);
  }

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedBeaconBlockAndBlobsSidecar> callback =
      mock(ResponseCallback.class);

  final BeaconBlockAndBlobsSidecarByRootMessageHandler handler =
      new BeaconBlockAndBlobsSidecarByRootMessageHandler(
          spec, eip4844ForkEpoch, storageSystem.getMetricsSystem(), recentChainData);

  @Test
  public void validateRequest_resourceNotAvailableWhenNotInSupportedRange() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    // 4098 - 4096 = 2
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(UInt64.valueOf(4098)));

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();

    when(recentChainData.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(finalizedEpoch.plus(1)));
    // requested epoch for blockRoot1 is earlier than the `minimum_request_epoch`
    when(recentChainData.getSlotForBlockRoot(blockRoot1)).thenReturn(Optional.of(UInt64.ONE));

    final BeaconBlockAndBlobsSidecarByRootRequestMessage request =
        new BeaconBlockAndBlobsSidecarByRootRequestMessage(List.of(blockRoot1, blockRoot2));

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValueSatisfying(
            rpcException -> {
              assertThat(rpcException.getResponseCode())
                  .isEqualTo(RpcResponseStatus.RESOURCE_UNAVAILABLE);
              assertThat(rpcException.getErrorMessageString())
                  .isEqualTo("Can't request block and blobs sidecar earlier than epoch 3");
            });
  }

  @Test
  public void onIncomingMessage_requestBlockAndBlobsSidecars() {
    when(recentChainData.getFinalizedEpoch()).thenReturn(finalizedEpoch);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(finalizedEpoch.plus(1)));
    when(recentChainData.getSlotForBlockRoot(any()))
        .thenReturn(Optional.of(finalizedEpoch.times(slotsPerEpoch)));

    final Bytes32 blockRoot1 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot2 = dataStructureUtil.randomBytes32();
    final Bytes32 blockRoot3 = dataStructureUtil.randomBytes32();

    final List<SignedBeaconBlockAndBlobsSidecar> expectedSent =
        mockBlocksAndBlobsSidecars(blockRoot1, blockRoot2, blockRoot3);

    final BeaconBlockAndBlobsSidecarByRootRequestMessage request =
        new BeaconBlockAndBlobsSidecarByRootRequestMessage(
            List.of(blockRoot1, blockRoot2, blockRoot3));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    final ArgumentCaptor<SignedBeaconBlockAndBlobsSidecar> argumentCaptor =
        ArgumentCaptor.forClass(SignedBeaconBlockAndBlobsSidecar.class);

    verify(callback, times(3)).respond(argumentCaptor.capture());

    final List<SignedBeaconBlockAndBlobsSidecar> actualSent = argumentCaptor.getAllValues();

    verify(callback).completeSuccessfully();

    assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  private List<SignedBeaconBlockAndBlobsSidecar> mockBlocksAndBlobsSidecars(
      final Bytes32... blockRoots) {
    return Arrays.stream(blockRoots)
        .map(
            blockRoot -> {
              final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar =
                  dataStructureUtil.randomSignedBeaconBlockAndBlobsSidecar();
              when(store.retrieveSignedBlockAndBlobsSidecar(blockRoot))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndBlobsSidecar)));
              return blockAndBlobsSidecar;
            })
        .collect(Collectors.toList());
  }
}
