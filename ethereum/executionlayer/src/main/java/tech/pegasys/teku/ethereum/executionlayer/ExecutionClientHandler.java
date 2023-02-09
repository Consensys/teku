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

package tech.pegasys.teku.ethereum.executionlayer;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.executionlayer.TransitionConfiguration;

public interface ExecutionClientHandler {

  SafeFuture<Optional<PowBlock>> eth1GetPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> eth1GetPowChainHead();

  SafeFuture<ForkChoiceUpdatedResult> engineForkChoiceUpdated(
      ForkChoiceState forkChoiceState,
      Optional<PayloadBuildingAttributes> payloadBuildingAttributes);

  SafeFuture<ExecutionPayloadWithValue> engineGetPayload(
      ExecutionPayloadContext executionPayloadContext, UInt64 slot);

  default SafeFuture<BlobsBundle> engineGetBlobsBundle(Bytes8 payloadId, UInt64 slot) {
    throw new IllegalArgumentException(
        String.format(
            "Pre-Deneb execution client handler is called to get Deneb BlobsBundleV1 for payload `%s`, slot %s",
            payloadId, slot));
  }

  SafeFuture<PayloadStatus> engineNewPayload(ExecutionPayload executionPayload);

  SafeFuture<TransitionConfiguration> engineExchangeTransitionConfiguration(
      TransitionConfiguration transitionConfiguration);
}
