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

package tech.pegasys.artemis.validator.coordinator;

import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.LocalMessageSignerService;

class ValidatorLoader {

  static Map<BLSPublicKey, ValidatorInfo> initializeValidators(ArtemisConfiguration config) {
    ValidatorKeyProvider keyProvider;
    if (config.getValidatorsKeyFile() != null) {
      keyProvider = new YamlValidatorKeyProvider();
    } else {
      keyProvider = new MockStartValidatorKeyProvider();
    }
    final List<BLSKeyPair> keypairs = keyProvider.loadValidatorKeys(config);
    final Map<BLSPublicKey, ValidatorInfo> validators = new HashMap<>();

    // Get validator connection info and create a new ValidatorInfo object and put it into the
    // Validators map
    for (int i = 0; i < keypairs.size(); i++) {
      BLSKeyPair keypair = keypairs.get(i);
      final LocalMessageSignerService signerService = new LocalMessageSignerService(keypair);
      STDOUT.log(Level.DEBUG, "Validator " + i + ": " + keypair.getPublicKey().toString());

      validators.put(keypair.getPublicKey(), new ValidatorInfo(signerService));
    }
    return validators;
  }
}
