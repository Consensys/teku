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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

public class AcceptanceTestBase {

  private final SimpleHttpClient httpClient = new SimpleHttpClient();
  private final List<ArtemisNode> nodes = new ArrayList<>();

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(ArtemisNode::stop);
  }

  protected ArtemisNode createArtemisNode() {
    final ArtemisNode artemisNode = new ArtemisNode(httpClient);
    nodes.add(artemisNode);
    return artemisNode;
  }
}
