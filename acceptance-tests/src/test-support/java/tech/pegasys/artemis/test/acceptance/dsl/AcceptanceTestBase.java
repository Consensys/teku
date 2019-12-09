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

package tech.pegasys.artemis.test.acceptance.dsl;

import com.github.dockerjava.api.model.Network.Ipam;
import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.Network;

public class AcceptanceTestBase {

  private static final String SUBNET_PREFIX = "10.105.47.";
  private final SimpleHttpClient httpClient = new SimpleHttpClient();
  private final List<ArtemisNode> nodes = new ArrayList<>();
  private int nextAllocation = 2;
  private final Supplier<Network> networkSupplier =
      Suppliers.memoize(
          () ->
              Network.builder()
                  .createNetworkCmdModifier(
                      modifier ->
                          modifier.withIpam(
                              new Ipam()
                                  .withConfig(
                                      new Ipam.Config().withSubnet(SUBNET_PREFIX + "0/24"))))
                  .build());

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(ArtemisNode::stop);
  }

  protected ArtemisNode createArtemisNode() {
    final ArtemisNode artemisNode =
        new ArtemisNode(httpClient, networkSupplier.get(), allocateNextIp());
    nodes.add(artemisNode);
    return artemisNode;
  }

  private String allocateNextIp() {
    final int allocation = nextAllocation++;
    return SUBNET_PREFIX + allocation;
  }
}
