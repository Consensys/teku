/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.validator;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class ValidatorImportResult {

  private final Optional<BLSPublicKey> maybePublicKey;
  private final Optional<KeyStoreData> maybeKeystoreData;
  private final PostKeyResult postKeyResult;
  private final String password;

  public Optional<BLSPublicKey> getPublicKey() {
    return this.maybePublicKey;
  }

  public Optional<KeyStoreData> getKeyStoreData() {
    return this.maybeKeystoreData;
  }

  public PostKeyResult getPostKeyResult() {
    return this.postKeyResult;
  }

  public String getPassword() {
    return this.password;
  }

  private ValidatorImportResult(ValidatorImportResultBuilder builder) {
    this.maybePublicKey = builder.maybePublicKey;
    this.maybeKeystoreData = builder.maybeKeystoreData;
    this.postKeyResult = builder.postKeyResult;
    this.password = builder.password;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorImportResult validatorImportResult = (ValidatorImportResult) o;
    return maybePublicKey.equals(validatorImportResult.maybePublicKey)
        && maybeKeystoreData.equals(validatorImportResult.maybeKeystoreData)
        && postKeyResult.equals(validatorImportResult.postKeyResult)
        && password.equals(validatorImportResult.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(maybePublicKey, maybeKeystoreData, postKeyResult, password);
  }

  public static class ValidatorImportResultBuilder {
    private Optional<BLSPublicKey> maybePublicKey = Optional.empty();
    private Optional<KeyStoreData> maybeKeystoreData = Optional.empty();
    private final PostKeyResult postKeyResult;

    private final String password;

    public ValidatorImportResultBuilder(final PostKeyResult postKeyResult, final String password) {
      this.postKeyResult = postKeyResult;
      this.password = password;
    }

    public ValidatorImportResultBuilder publicKey(Optional<BLSPublicKey> publicKey) {
      this.maybePublicKey = publicKey;
      return this;
    }

    public ValidatorImportResultBuilder keyStoreData(Optional<KeyStoreData> keystoreData) {
      this.maybeKeystoreData = keystoreData;
      return this;
    }

    public ValidatorImportResult build() {
      return new ValidatorImportResult(this);
    }
  }
}
