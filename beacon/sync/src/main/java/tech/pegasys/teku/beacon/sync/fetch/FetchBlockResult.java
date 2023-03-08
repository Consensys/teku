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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public final class FetchBlockResult {

  public enum Status {
    SUCCESSFUL,
    NO_AVAILABLE_PEERS,
    CANCELLED,
    FETCH_FAILED
  }

  private final Status status;
  private final Optional<SignedBeaconBlock> block;

  private FetchBlockResult(final Status status, final Optional<SignedBeaconBlock> block) {
    this.status = status;
    this.block = block;
  }

  public static FetchBlockResult createSuccessful(final SignedBeaconBlock block) {
    return new FetchBlockResult(Status.SUCCESSFUL, Optional.of(block));
  }

  public static FetchBlockResult createFailed(final Status failureStatus) {
    return new FetchBlockResult(failureStatus, Optional.empty());
  }

  public boolean isSuccessful() {
    return status == Status.SUCCESSFUL;
  }

  public Optional<SignedBeaconBlock> getBlock() {
    return block;
  }

  public FetchBlockResult.Status getStatus() {
    return status;
  }
}
