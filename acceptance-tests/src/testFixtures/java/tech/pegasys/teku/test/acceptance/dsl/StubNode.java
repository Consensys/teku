/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.test.acceptance.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class StubNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public static final int STUB_PORT = 8001;
  protected static final String WORKING_DIRECTORY =
      "/opt/tech/pegasys/teku/test/acceptance/stubServer/";

  private static final Network network = Network.newNetwork();
  private boolean started = false;

  private StubNode(Network network, ImageFromDockerfile image) {
    super(network, image);
    container.withWorkingDirectory(WORKING_DIRECTORY).withExposedPorts(STUB_PORT);
  }

  public static StubNode create() {
    final ImageFromDockerfile image =
        new ImageFromDockerfile()
            .withDockerfile(
                Path.of(
                    "src/testFixtures/java/tech/pegasys/teku/test/acceptance/stubServer/Dockerfile"));
    return new StubNode(network, image);
  }

  public String getAddress() {
    return "http://" + this.container.getHost() + ":" + this.container.getMappedPort(STUB_PORT);
  }

  public Network getNetwork() {
    return network;
  }

  public String getContainerNetworkAlias() {
    return this.nodeAlias;
  }

  public void start() {
    assertThat(started).isFalse();
    LOG.debug("Start node {}", nodeAlias);
    started = true;
    container.start();
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    container.stop();
  }
}
