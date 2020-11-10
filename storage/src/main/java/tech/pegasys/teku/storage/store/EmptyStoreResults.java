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

package tech.pegasys.teku.storage.store;

import java.util.Optional;
import tech.pegasys.teku.core.stategenerator.StateGenerationTask;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public abstract class EmptyStoreResults {

  public static SafeFuture<Optional<SignedBeaconBlock>> EMPTY_SIGNED_BLOCK_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static SafeFuture<Optional<BeaconBlock>> EMPTY_BLOCK_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static SafeFuture<Optional<BeaconState>> EMPTY_STATE_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static SafeFuture<Optional<SignedBlockAndState>> EMPTY_BLOCK_AND_STATE_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static SafeFuture<Optional<StateAndBlockSummary>> EMPTY_STATE_AND_BLOCK_SUMMARY_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static SafeFuture<Optional<StateGenerationTask>> EMPTY_STATE_GENERATION_TASK =
      SafeFuture.completedFuture(Optional.empty());
}
