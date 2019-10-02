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
import tech.pegasys.artemis.util.alogger.ALogger;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class RandomValidatorKeyProvider implements ValidatorKeyProvider {

  private static final ALogger STDOUT = new ALogger("stdout");

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final int startIndex, final int endIndex) {
    List<BLSKeyPair> keypairs = new ArrayList<>();
    for (int i = startIndex; i <= endIndex; i++) {
      BLSKeyPair keypair = BLSKeyPair.random(i);
      keypairs.add(keypair);
    }
    return keypairs;
  }
}
