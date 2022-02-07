/*
 * Copyright 2022 ConsenSys AG.
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

import java.net.URL;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;

public class ExternalValidator {
  private BLSPublicKey publicKey;
  private Optional<URL> url;
  private boolean readOnly;

  public ExternalValidator() {}

  public ExternalValidator(BLSPublicKey publicKey, Optional<URL> url, boolean readOnly) {
    this.publicKey = publicKey;
    this.url = url;
    this.readOnly = readOnly;
  }

  public BLSPublicKey getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(BLSPublicKey publicKey) {
    this.publicKey = publicKey;
  }

  public Optional<URL> getUrl() {
    return url;
  }

  public void setUrl(Optional<URL> url) {
    this.url = url;
  }

  public boolean isReadOnly() {
    return readOnly;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }
}
