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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;

public class RandomValidatorKeyProvider implements ValidatorKeyProvider {

  private static final ALogger STDOUT = new ALogger("stdout");
  private int startIndex = 0;

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final ArtemisConfiguration config) {
    SECP256K1.SecretKey nodeIdentity =
        SECP256K1.SecretKey.fromBytes(Bytes32.fromHexString(config.getIdentity()));
    int numValidators = config.getNumValidators();
    int numNodes = config.getNumNodes();

    Pair<Integer, Integer> startAndEnd = getStartAndEnd(nodeIdentity, numNodes, numValidators);
    int startIndex = startAndEnd.getLeft();
    int endIndex = startAndEnd.getRight();
    List<BLSKeyPair> keypairs = new ArrayList<>();
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      keypairs.add(keypair);
    }

    this.startIndex = startIndex;
    return keypairs;
  }

  private Pair<Integer, Integer> getStartAndEnd(
      SECP256K1.SecretKey nodeIdentity, int numNodes, int numValidators) {
    /*
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();
    int startIndex = 0;
    int endIndex = numValidators - 1;
    if (nodeCounter > 0) {
      endIndex = -1;
    }
    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + Math.floorDiv(nodeCounter, Math.max(1, numNodes - 1));
    endIndex = Math.min(endIndex, numValidators - 1);
    STDOUT.log(
        Level.INFO,
        "nodeCounter: " + nodeCounter + " startIndex: " + startIndex + " endIndex: " + endIndex);
    return new ImmutablePair<>(startIndex, endIndex);
    */
    // Add all validators to validatorSet hashMap
    int nodeCounter = UInt256.fromBytes(nodeIdentity.bytes()).mod(numNodes).intValue();
    int startIndex = nodeCounter * (numValidators / numNodes);
    int endIndex =
        startIndex
            + (numValidators / numNodes - 1)
            + Math.floorDiv(nodeCounter, Math.max(1, numNodes - 1));
    endIndex = Math.min(endIndex, numValidators - 1);

    STDOUT.log(
        Level.INFO,
        "nodeCounter: " + nodeCounter + " startIndex: " + startIndex + " endIndex: " + endIndex);
    return new ImmutablePair<>(startIndex, endIndex);
  }

  @Override
  public int getValidatorPortStartIndex() {
    return startIndex;
  }
}
