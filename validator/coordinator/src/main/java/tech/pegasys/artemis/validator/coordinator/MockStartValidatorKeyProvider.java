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

import static tech.pegasys.teku.logging.ContextualLogger.STDOUT;

import java.util.List;
import org.apache.logging.log4j.Level;
import tech.pegasys.artemis.datastructures.util.MockStartValidatorKeyPairFactory;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.teku.logging.ContextualLogger.Color;

class MockStartValidatorKeyProvider implements ValidatorKeyProvider {

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final ArtemisConfiguration config) {
    final int startIndex = config.getInteropOwnedValidatorStartIndex();
    final int endIndex = startIndex + config.getInteropOwnedValidatorCount();
    STDOUT.log(
        Level.DEBUG, "Owning validator range " + startIndex + " to " + endIndex, Color.GREEN);
    return new MockStartValidatorKeyPairFactory().generateKeyPairs(startIndex, endIndex);
  }
}
