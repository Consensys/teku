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

package tech.pegasys.teku.api.schema.capella;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;

public class HistoricalSummary {

  @JsonProperty("block_summary_root")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 blockSummaryRoot;

  @JsonProperty("state_summary_root")
  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 stateSummaryRoot;

  public HistoricalSummary(
      @JsonProperty("block_summary_root") final Bytes32 blockSummaryRoot,
      @JsonProperty("state_summary_root") final Bytes32 stateSummaryRoot) {
    this.blockSummaryRoot = blockSummaryRoot;
    this.stateSummaryRoot = stateSummaryRoot;
  }

  public HistoricalSummary(
      final tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary
          internalSummary) {
    this.blockSummaryRoot = internalSummary.getBlockSummaryRoot().get();
    this.stateSummaryRoot = internalSummary.getStateSummaryRoot().get();
  }

  public tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary
      asInternal() {
    return tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary.SSZ_SCHEMA
        .create(SszBytes32.of(blockSummaryRoot), SszBytes32.of(stateSummaryRoot));
  }
}
