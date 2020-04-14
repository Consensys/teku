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

package tech.pegasys.artemis.api.schema;

public class SignedBeaconBlockHeader {
  public final BeaconBlockHeader message;
  public final BLSSignature signature;

  public SignedBeaconBlockHeader(
      tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlockHeader signedHeader) {
    this.message = new BeaconBlockHeader(signedHeader.getMessage());
    this.signature = new BLSSignature(signedHeader.getSignature());
  }

  public tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlockHeader
      asInternalSignedBeaconBlockHeader() {
    return new tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlockHeader(
        message.asInternalBeaconBlockHeader(), signature.asInternalBLSSignature());
  }
}
