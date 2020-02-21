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

package tech.pegasys.artemis.util.bls.keystore;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.apache.tuweni.bytes.Bytes;

public abstract class KdfParam extends Param {
  private final Integer dklen;
  private final Bytes salt;

  public KdfParam(final Integer dklen, final Bytes salt) {
    this.dklen = dklen;
    this.salt = salt;
  }

  @JsonProperty(value = "dklen")
  public Integer getDerivedKeyLength() {
    return dklen;
  }

  @JsonProperty(value = "salt")
  public Bytes getSalt() {
    return salt;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("dklen", dklen).add("salt", salt).toString();
  }
}
