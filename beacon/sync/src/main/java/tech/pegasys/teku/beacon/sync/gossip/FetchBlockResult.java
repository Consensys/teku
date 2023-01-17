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

package tech.pegasys.teku.beacon.sync.gossip;

import java.util.NoSuchElementException;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.eip4844.SignedBeaconBlockAndBlobsSidecar;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar;

public final class FetchBlockResult {

  public enum Status {
    SUCCESSFUL,
    NO_AVAILABLE_PEERS,
    CANCELLED,
    FETCH_FAILED
  }

  private final Status status;
  private final Optional<SignedBeaconBlock> block;
  private final Optional<BlobsSidecar> blobsSidecar;

  private FetchBlockResult(
      final Status status,
      final Optional<SignedBeaconBlock> block,
      final Optional<BlobsSidecar> blobsSidecar) {
    this.status = status;
    this.block = block;
    this.blobsSidecar = blobsSidecar;
  }

  public static FetchBlockResult createSuccessful(final SignedBeaconBlock block) {
    return new FetchBlockResult(Status.SUCCESSFUL, Optional.of(block), Optional.empty());
  }

  public static FetchBlockResult createSuccessful(
      final SignedBeaconBlockAndBlobsSidecar blockAndBlobsSidecar) {
    return new FetchBlockResult(
        Status.SUCCESSFUL,
        Optional.of(blockAndBlobsSidecar.getSignedBeaconBlock()),
        Optional.of(blockAndBlobsSidecar.getBlobsSidecar()));
  }

  public static FetchBlockResult createFailed(final FetchBlockResult.Status failureStatus) {
    return new FetchBlockResult(failureStatus, Optional.empty(), Optional.empty());
  }

  public boolean isSuccessful() {
    return status == Status.SUCCESSFUL;
  }

  public SignedBeaconBlock getBlock() throws NoSuchElementException {
    return block.orElseThrow();
  }

  public Optional<BlobsSidecar> getBlobsSidecar() {
    return blobsSidecar;
  }

  public FetchBlockResult.Status getStatus() {
    return status;
  }
}
