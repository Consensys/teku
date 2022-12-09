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

package tech.pegasys.teku.api.schema.altair;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.schema.BeaconBlockHeader;

public class LightClientBootstrap {

  public final BeaconBlockHeader header;

  public final SyncCommittee currentSyncCommittee;

  @JsonProperty("current_sync_committee_branch")
  @ArraySchema(
      schema = @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32))
  public final List<Bytes32> currentSyncCommitteeBranch;

  @JsonCreator
  public LightClientBootstrap(
      @JsonProperty("header") final BeaconBlockHeader header,
      @JsonProperty("current_sync_committee") final SyncCommittee currentSyncCommittee,
      @JsonProperty("current_sync_committee_branch")
          final List<Bytes32> currentSyncCommitteeBranch) {
    this.header = header;
    this.currentSyncCommittee = currentSyncCommittee;
    this.currentSyncCommitteeBranch = currentSyncCommitteeBranch;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LightClientBootstrap that = (LightClientBootstrap) o;
    return Objects.equals(header, that.header)
        && Objects.equals(currentSyncCommittee, that.currentSyncCommittee)
        && Objects.equals(currentSyncCommitteeBranch, that.currentSyncCommitteeBranch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(header, currentSyncCommittee, currentSyncCommitteeBranch);
  }
}
