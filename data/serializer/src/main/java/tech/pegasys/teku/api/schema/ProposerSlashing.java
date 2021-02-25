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

package tech.pegasys.teku.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class ProposerSlashing {
  public final SignedBeaconBlockHeader signed_header_1;
  public final SignedBeaconBlockHeader signed_header_2;

  public ProposerSlashing(
      tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing proposerSlashing) {
    signed_header_1 = new SignedBeaconBlockHeader(proposerSlashing.getHeader_1());
    signed_header_2 = new SignedBeaconBlockHeader(proposerSlashing.getHeader_2());
  }

  @JsonCreator
  public ProposerSlashing(
      @JsonProperty("signed_header_1") final SignedBeaconBlockHeader header_1,
      @JsonProperty("signed_header_2") final SignedBeaconBlockHeader header_2) {
    this.signed_header_1 = header_1;
    this.signed_header_2 = header_2;
  }

  public tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing
      asInternalProposerSlashing() {
    return new tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing(
        signed_header_1.asInternalSignedBeaconBlockHeader(),
        signed_header_2.asInternalSignedBeaconBlockHeader());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProposerSlashing)) return false;
    ProposerSlashing that = (ProposerSlashing) o;
    return Objects.equals(signed_header_1, that.signed_header_1)
        && Objects.equals(signed_header_2, that.signed_header_2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signed_header_1, signed_header_2);
  }
}
