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

package tech.pegasys.teku.validator.client.loader;

import java.util.List;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;

public interface ValidatorSource {
  List<ValidatorProvider> getAvailableValidators();

  boolean canUpdateValidators();

  DeleteKeyResult deleteValidator(BLSPublicKey publicKey);

  AddLocalValidatorResult addValidator(
      KeyStoreData keyStoreData, String password, BLSPublicKey publicKey);

  interface ValidatorProvider {
    BLSPublicKey getPublicKey();

    Signer createSigner();

    boolean isReadOnly();
  }
}
