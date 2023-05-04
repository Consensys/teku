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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import java.util.Objects;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.GetPayloadResponseCapella;

public class GetPayloadResponseDeneb extends GetPayloadResponseCapella {

  private final BlobsBundle blobsBundle;

  public GetPayloadResponseDeneb(
      final ExecutionPayload executionPayload,
      final UInt256 blockValue,
      final BlobsBundle blobsBundle) {
    super(executionPayload, blockValue);
    this.blobsBundle = blobsBundle;
  }

  @Override
  public BlobsBundle getBlobsBundle() {
    return blobsBundle;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof GetPayloadResponseDeneb)) return false;
    if (!super.equals(o)) return false;
    final GetPayloadResponseDeneb that = (GetPayloadResponseDeneb) o;
    return Objects.equals(blobsBundle, that.blobsBundle);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), blobsBundle);
  }
}
