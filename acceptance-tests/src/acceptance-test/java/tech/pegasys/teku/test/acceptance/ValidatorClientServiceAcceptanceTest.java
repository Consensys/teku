/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class ValidatorClientServiceAcceptanceTest extends AcceptanceTestBase {

  @Test
  void shouldFailWithNoValidatorKeysWhenExitOptionEnabled() throws Exception {
    TekuNode beaconNode = createTekuNode(config -> config.withExitWhenNoValidatorKeysEnabled(true));
    beaconNode.startWithFailure(
        "No loaded validators when --exit-when-no-validator-keys-enabled option is false");
  }
}
