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

package tech.pegasys.teku.validator.client.loader;

import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.util.config.TekuConfiguration;

class MockStartValidatorKeyProvider implements ValidatorKeyProvider {

  private static final Logger LOG = LogManager.getLogger();

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final TekuConfiguration config) {
    final int startIndex = config.getInteropOwnedValidatorStartIndex();
    final int endIndex = startIndex + config.getInteropOwnedValidatorCount();
    LOG.log(Level.DEBUG, "Owning validator range " + startIndex + " to " + endIndex);
    return new MockStartValidatorKeyPairFactory().generateKeyPairs(startIndex, endIndex);
  }
}
