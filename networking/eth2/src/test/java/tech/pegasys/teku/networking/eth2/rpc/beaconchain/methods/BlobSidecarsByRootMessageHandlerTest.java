/*
 * Copyright ConsenSys Software Inc., 2023
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
import static tech.pegasys.teku.spec.config.Constants.MAX_CHUNK_SIZE_BELLATRIX;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsSidecar;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarsByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(MAX_CHUNK_SIZE_BELLATRIX);

  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRootMethodId(1, RPC_ENCODING);

  private final UInt64 denebForkEpoch = UInt64.valueOf(1);

  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobsSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final UInt64 maxRequestSize = UInt64.valueOf(8);

  BlobSidecarsByRootMessageHandler handler =
      new BlobSidecarsByRootMessageHandler(
          spec, denebForkEpoch, combinedChainDataClient, maxRequestSize);

  @Test
  public void shouldSendResourceUnavailableIfBlockForBlockRootIsNotAvailable() {}

  @Test
  public void
      shouldSendResourceUnavailableIfBlockRootReferencesBlockEarlierThanTheMinimumRequestEpoch() {}

  @Test
  public void shouldSendResourceUnavailableIfBlobSidecarIsNotAvailable() {}

  @Test
  public void shouldSendToPeerRequestedBlobSidecars() {}
}
