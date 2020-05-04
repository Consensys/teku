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

import com.fasterxml.jackson.annotation.JsonProperty;

public class SignedVoluntaryExit {
  public final VoluntaryExit message;
  public final BLSSignature signature;

  public SignedVoluntaryExit(
      tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit signedVoluntaryExit) {
    this.signature = new BLSSignature(signedVoluntaryExit.getSignature());
    this.message = new VoluntaryExit(signedVoluntaryExit.getMessage());
  }

  public SignedVoluntaryExit(
      @JsonProperty("message") final VoluntaryExit message,
      @JsonProperty("signature") final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit
      asInternalSignedVoluntaryExit() {
    return new tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit(
        message.asInternalVoluntaryExit(), signature.asInternalBLSSignature());
  }
}
