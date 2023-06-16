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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

/**
 * Classes implementing this interface MUST:
 *
 * <p>- provide via {@link #getSignedBlindedBlockContainer()} the {@link
 * SignedBlindedBlockContainer} on which we are about to apply the unblinding process
 *
 * <p>- expect {@link #setExecutionPayloadSupplier( Supplier)} to be called, which provides a future
 * retrieving an ExecutionPayload consistent with the ExecutionPayloadHeader included in the Blinded
 * Block
 *
 * <p>- expect the {@link #unblind()} method to be called after {@link #setExecutionPayloadSupplier(
 * Supplier)}.
 *
 * <p>- {@link #unblind()} now has all the information (Blinded Block + ExecutionPayload) to
 * construct the unblinded version of the {@link SignedBeaconBlock}
 */
public interface SignedBeaconBlockUnblinder {
  void setExecutionPayloadSupplier(Supplier<SafeFuture<ExecutionPayload>> executionPayloadSupplier);

  SignedBlindedBlockContainer getSignedBlindedBlockContainer();

  SafeFuture<SignedBeaconBlock> unblind();
}
