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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.units.bigints.UInt256;
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
    SECP256K1.SecretKey nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getIdentity()));
    int numNodes = config.getNumNodes();
    int nodeNum = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();
    boolean interopActive = config.getInteropActive();

    long numNaughtyValidators = Math.round((naughtinessPercentage * numValidators) / 100.0);
    ValidatorKeyProvider keyProvider;
    int startIndex = 0;
    int endIndex = numValidators - 1;
    if (config.getValidatorsKeyFile() != null) {
      Path keyFile = Path.of(config.getValidatorsKeyFile());
      keyProvider = new YamlValidatorKeyProvider(keyFile);
    } else if (interopActive) {
      startIndex = config.getInteropOwnedValidatorStartIndex();
      endIndex = startIndex + config.getInteropOwnedValidatorCount() - 1;
      keyProvider = new MockStartValidatorKeyProvider();
    } else {
      startIndex = nodeNum * (numValidators / numNodes);
      endIndex =
          startIndex
              + (numValidators / numNodes - 1)
              + Math.floorDiv(nodeNum, Math.max(1, numNodes - 1));
      endIndex = Math.min(endIndex, numValidators - 1);
      keyProvider = new RandomValidatorKeyProvider();
    }
    final List<BLSKeyPair> keypairs = keyProvider.loadValidatorKeys(startIndex, endIndex);
    final Map<BLSPublicKey, ValidatorInfo> validators = new HashMap<>();

    for (int i = 0; i < keypairs.size(); i++) {
      BLSKeyPair keypair = keypairs.get(i);
      int port = Constants.VALIDATOR_CLIENT_PORT_BASE + startIndex + i;
      new ValidatorClient(keypair, port);
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
      STDOUT.log(Level.DEBUG, "i = " + i + ": " + keypair.getPublicKey().toString());

      validators.put(keypair.getPublicKey(), new ValidatorInfo(numNaughtyValidators > 0, channel));
      numNaughtyValidators--;
    }
    return validators;
  }
}
