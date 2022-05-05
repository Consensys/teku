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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;

public class ProposerSlashingV1 {
  public final SignedMessage<BeaconBlockHeaderV1> signedHeader1;
  public final SignedMessage<BeaconBlockHeaderV1> signedHeader2;

  @JsonCreator
  public ProposerSlashingV1(
      @JsonProperty("signedHeader1") final SignedMessage<BeaconBlockHeaderV1> header1,
      @JsonProperty("signedHeader2") final SignedMessage<BeaconBlockHeaderV1> header2) {
    this.signedHeader1 = header1;
    this.signedHeader2 = header2;
  }

  public ProposerSlashingV1(
      tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing proposerSlashing) {
    signedHeader1 =
        new SignedMessage<BeaconBlockHeaderV1>(
            new BeaconBlockHeaderV1(proposerSlashing.getHeader1().getMessage()),
            proposerSlashing.getHeader1().getSignature());
    signedHeader2 =
        new SignedMessage<BeaconBlockHeaderV1>(
            new BeaconBlockHeaderV1(proposerSlashing.getHeader2().getMessage()),
            proposerSlashing.getHeader2().getSignature());
  }

  public tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing
      asInternalProposerSlashing() {
    return new tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing(
        new SignedBeaconBlockHeader(
            signedHeader1.getMessage().asInternalBeaconBlockHeader(),
            signedHeader1.getSignature().asInternalBLSSignature()),
        new SignedBeaconBlockHeader(
            signedHeader2.getMessage().asInternalBeaconBlockHeader(),
            signedHeader2.getSignature().asInternalBLSSignature()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProposerSlashingV1)) {
      return false;
    }
    ProposerSlashingV1 that = (ProposerSlashingV1) o;
    return Objects.equals(signedHeader1, that.signedHeader1)
        && Objects.equals(signedHeader2, that.signedHeader2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(signedHeader1, signedHeader2);
  }
}
