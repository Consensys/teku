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

package tech.pegasys.teku.api.schema.eip4844;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES48;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.Blob;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecarSchema;

public class BlobsSidecar {

  @JsonProperty("beacon_block_root")
  private final Bytes32 beaconBlockRoot;

  @JsonProperty("beacon_block_slot")
  private final UInt64 beaconBlockSlot;

  @ArraySchema(schema = @Schema(type = "string", format = "byte"))
  public final List<Bytes> blobs;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES48)
  @JsonProperty("kzg_aggregated_proof")
  private final Bytes kzgAggregatedProof;

  public BlobsSidecar(
      @JsonProperty("beacon_block_root") final Bytes32 beaconBlockRoot,
      @JsonProperty("beacon_block_slot") final UInt64 beaconBlockSlot,
      @JsonProperty("blobs") final List<Bytes> blobs,
      @JsonProperty("kzg_aggregated_proof") final Bytes kzgAggregatedProof) {
    this.beaconBlockRoot = beaconBlockRoot;
    this.beaconBlockSlot = beaconBlockSlot;
    this.blobs = blobs;
    this.kzgAggregatedProof = kzgAggregatedProof;
  }

  public BlobsSidecar(
      final tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar
          blobSidecar) {
    this.beaconBlockRoot = blobSidecar.getBeaconBlockRoot();
    this.beaconBlockSlot = blobSidecar.getBeaconBlockSlot();
    this.blobs = blobSidecar.getBlobs().stream().map(Blob::getBytes).collect(Collectors.toList());
    this.kzgAggregatedProof = blobSidecar.getKZGAggregatedProof().getBytesCompressed();
  }

  public static BlobsSidecar create(
      final tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar
          blobSidecar) {
    return new BlobsSidecar(blobSidecar);
  }

  public tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsSidecar
      asInternalBlobSidecar(final BlobsSidecarSchema schema) {
    return schema.create(beaconBlockRoot, beaconBlockSlot, blobs, Bytes48.wrap(kzgAggregatedProof));
  }
}
