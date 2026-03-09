/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.test.acceptance.dsl.TekuBootnodeNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfig;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class BootnodeModeAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldStartupBootnodeNode() throws Exception {
    final TekuNodeConfig tekuNodeConfig = TekuNodeConfigBuilder.createBootnode().build();
    final TekuBootnodeNode bootnode = createBootnode(tekuNodeConfig);

    bootnode.start();
    bootnode.waitForDiscoveryStarted();
  }

  @Test
  public void shouldStartupBootnodeNodeWithSpecificNodeKey() throws Exception {
    final TekuNodeConfig tekuNodeConfig =
        TekuNodeConfigBuilder.createBootnode(
                "0x2ddca91e4bca641eada438600ce9983f5018b8b52c258099b5dcaec10f577aa7")
            .build();
    final TekuBootnodeNode bootnode = createBootnode(tekuNodeConfig);

    // Because we are using a fixed key, we expect a consistent ENR
    final String expectedENR =
        "enr:-HW4QJlG0xiXxcVx6hY5pb9YqgqRZZZwt6ivT-_svu_ulv4nZbEJrmlB6C7OX8QgeSESJc86Azxm-4BRhkHeKaW"
            + "-9qMBgmlkgnY0iXNlY3AyNTZrMaECwL8KmA6UC9dKOdW9yhHfXyjOQzjpGfDvJqT7oLQpilg";

    bootnode.start();
    bootnode.waitForDiscoveryStarted();
    bootnode.waitForNodeENR(expectedENR);
  }
}
