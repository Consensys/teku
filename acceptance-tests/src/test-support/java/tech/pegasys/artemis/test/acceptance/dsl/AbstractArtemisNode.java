package tech.pegasys.artemis.test.acceptance.dsl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class AbstractArtemisNode extends Node {
  private static final Logger LOG = LogManager.getLogger();
  protected final GenericContainer<?> container;

  public AbstractArtemisNode(final Network network) {
    container =
        new GenericContainer<>("pegasyseng/artemis:develop")
            .withNetwork(network)
            .withNetworkAliases(nodeAlias)
            .withLogConsumer(frame -> LOG.debug(frame.getUtf8String().trim()));
  }

  @Override
  public void stop() {
    container.stop();
  }
}
