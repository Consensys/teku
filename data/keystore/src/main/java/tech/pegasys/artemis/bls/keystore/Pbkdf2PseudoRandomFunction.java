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

package tech.pegasys.artemis.bls.keystore;

import com.fasterxml.jackson.annotation.JsonValue;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.util.DigestFactory;

public enum Pbkdf2PseudoRandomFunction {
  HMAC_SHA1("hmac-sha1"),
  HMAC_SHA256("hmac-sha256"),
  HMAC_SHA512("hmac-sha512");

  private final String jsonValue;

  Pbkdf2PseudoRandomFunction(final String jsonValue) {
    this.jsonValue = jsonValue;
  }

  @JsonValue
  public String getJsonValue() {
    return this.jsonValue;
  }

  public Digest getDigest() {
    switch (this) {
      case HMAC_SHA256:
        return DigestFactory.createSHA256();
      case HMAC_SHA512:
        return DigestFactory.createSHA512();
      default:
        return DigestFactory.createSHA1();
    }
  }
}
