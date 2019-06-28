/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;

public final class RequestAttestationMessage {

  private final Bytes32 attestationHash;

  @JsonCreator
  public RequestAttestationMessage(@JsonProperty("attestationHash") Bytes32 attestationHash) {
    this.attestationHash = attestationHash;
  }

  @JsonProperty("attestationHash")
  public Bytes32 attestationHash() {
    return attestationHash;
  }
}
