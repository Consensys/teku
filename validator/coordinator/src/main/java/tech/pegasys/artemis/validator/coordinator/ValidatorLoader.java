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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.validator.client.ValidatorClient;

public class ValidatorLoader {
  private static final ALogger STDOUT = new ALogger("stdout");

  public Map<BLSPublicKey, ValidatorInfo> initializeValidators(ArtemisConfiguration config) {
    int naughtinessPercentage = config.getNaughtinessPercentage();
    int numValidators = config.getNumValidators();
    boolean interopActive = config.getInteropActive();

    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
    ValidatorKeyProvider keyProvider;
    if (config.getValidatorsKeyFile() != null) {
      keyProvider = new YamlValidatorKeyProvider();
    } else if (interopActive) {
      keyProvider = new MockStartValidatorKeyProvider();
    } else {
      keyProvider = new RandomValidatorKeyProvider();
    }
    final List<BLSKeyPair> keypairs = keyProvider.loadValidatorKeys(config);
    final Map<BLSPublicKey, ValidatorInfo> validators = new HashMap<>();

    for (int i = 0; i < keypairs.size(); i++) {
      BLSKeyPair keypair = keypairs.get(i);
      int port =
          Constants.VALIDATOR_CLIENT_PORT_BASE + i + keyProvider.getValidatorPortStartIndex();
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      STDOUT.log(Level.INFO, "i = " + i + ": " + keypair.getPublicKey().toString());

      validators.put(keypair.getPublicKey(), new ValidatorInfo(numNaughtyValidators > 0, channel));
      numNaughtyValidators--;
    }
    return validators;
  }
}
