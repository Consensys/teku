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

import static tech.pegasys.artemis.statetransition.util.ForkChoiceUtil.get_head;
import static tech.pegasys.artemis.util.alogger.ALogger.STDOUT;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.config.Constants;
import tech.pegasys.artemis.validator.client.ValidatorClient;

class ValidatorLoader {

  static Map<BLSPublicKey, ValidatorInfo> initializeValidators(
      ArtemisConfiguration config, ChainStorageClient chainStorageClient) {
    int naughtinessPercentage = config.getNaughtinessPercentage();
    int numValidators = config.getNumValidators();

    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
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
      int port =
          Constants.VALIDATOR_CLIENT_PORT_BASE + i + keyProvider.getValidatorPortStartIndex();
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      STDOUT.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());

      validators.put(keypair.getPublicKey(), new ValidatorInfo(numNaughtyValidators > 0, channel));
      numNaughtyValidators--;
    }

    final Store store = chainStorageClient.getStore();
    final Bytes32 head = get_head(store);
    final BeaconState genesisState = store.getBlockState(head);

    // Get validator indices of our own validators
    List<Validator> validatorRegistry = genesisState.getValidators();
    IntStream.range(0, validatorRegistry.size())
        .forEach(
            i -> {
              if (validators.containsKey(validatorRegistry.get(i).getPubkey())) {
                STDOUT.log(
                    Level.DEBUG,
                    "owned index = " + i + ": " + validatorRegistry.get(i).getPubkey());
                validators.get(validatorRegistry.get(i).getPubkey()).setValidatorIndex(i);
              }
            });

    return validators;
  }
}
