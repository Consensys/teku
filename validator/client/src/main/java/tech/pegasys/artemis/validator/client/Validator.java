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

package tech.pegasys.artemis.validator.client;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.mikuli.KeyPair;

public final class Validator extends tech.pegasys.artemis.datastructures.state.Validator {
  KeyPair blsKeys;
  SECP256K1.KeyPair secpKeys;

  public Validator(Bytes32 withdrawal_credentials, KeyPair blsKeys, SECP256K1.KeyPair secpKeys) {
    super(null, withdrawal_credentials, null, null, null, false, false);
    this.blsKeys = blsKeys;
    this.secpKeys = secpKeys;
  }

  public KeyPair getBlsKeys() {
    return blsKeys;
  }

  public SECP256K1.KeyPair getSecpKeys() {
    return secpKeys;
  }

  @Override
  public BLSPublicKey getPubkey() {
    return BLSPublicKey.fromBytesCompressed(
        this.blsKeys.publicKey().toBytesCompressed());
  }
}
