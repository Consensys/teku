/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.bls.BLSPublicKey;

public class ActiveValidator {
  private final BLSPublicKey publicKey;
  private final boolean readonly;

  public ActiveValidator(final BLSPublicKey publicKey, final boolean readonly) {
    this.publicKey = publicKey;
    this.readonly = readonly;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public boolean isReadonly() {
    return readonly;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("active", readonly)
        .toString();
  }
}
