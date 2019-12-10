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

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.Network;

public class AcceptanceTestBase {

  private final SimpleHttpClient httpClient = new SimpleHttpClient();
  private final List<Node> nodes = new ArrayList<>();
  private final Supplier<Network> networkSupplier = Suppliers.memoize(Network::newNetwork);

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(Node::stop);
  }

  protected ArtemisNode createArtemisNode() {
    return createArtemisNode(config -> {});
  }

  protected ArtemisNode createArtemisNode(final Consumer<ArtemisNode.Config> configOptions) {
    final ArtemisNode artemisNode =
        new ArtemisNode(httpClient, networkSupplier.get(), configOptions);
    nodes.add(artemisNode);
    return artemisNode;
  }

  protected BesuNode createBesuNode() {
    final BesuNode besuNode = new BesuNode(httpClient, networkSupplier.get());
    nodes.add(besuNode);
    return besuNode;
  }
}
