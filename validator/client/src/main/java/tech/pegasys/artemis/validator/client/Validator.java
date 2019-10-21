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

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;

public final class Validator extends tech.pegasys.artemis.datastructures.state.Validator {
  BLSKeyPair blsKeys;
  SECP256K1.KeyPair secpKeys;

  public Validator(Bytes32 withdrawal_credentials, BLSKeyPair blsKeys, SECP256K1.KeyPair secpKeys) {
    super(
        null,
        withdrawal_credentials,
        UnsignedLong.ZERO,
        false,
        UnsignedLong.ZERO,
        UnsignedLong.ZERO,
        UnsignedLong.ZERO,
        UnsignedLong.ZERO);
    this.blsKeys = blsKeys;
    this.secpKeys = secpKeys;
  }

  public BLSKeyPair getBlsKeys() {
    return blsKeys;
  }

  public SECP256K1.KeyPair getSecpKeys() {
    return secpKeys;
  }

  @Override
  public BLSPublicKey getPubkey() {
    return this.blsKeys.getPublicKey();
  }
}
