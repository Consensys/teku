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

package tech.pegasys.teku.api.response.v1.validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class ProposerDuties {

  @JsonProperty("current_target_root")
  public final Bytes32 currentTargetRoot;

  public final List<ProposerDuty> duties;

  public ProposerDuties(
      @JsonProperty("current_target_root") final Bytes32 currentTargetRoot,
      @JsonProperty("duties") final List<ProposerDuty> duties) {
    this.currentTargetRoot = currentTargetRoot;
    this.duties = duties;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerDuties that = (ProposerDuties) o;
    return Objects.equals(currentTargetRoot, that.currentTargetRoot)
        && Objects.equals(duties, that.duties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentTargetRoot, duties);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentTargetRoot", currentTargetRoot)
        .add("duties", duties)
        .toString();
  }
}
