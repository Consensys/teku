package tech.pegasys.artemis.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.artemis.test.acceptance.dsl.ArtemisNode;

public class MockGenesisStartupAcceptanceTest extends AcceptanceTestBase {

  @Test
  public void shouldProgressChainAfterStartingFromMockGenesis() throws Exception {
    final ArtemisNode node = createArtemisNode();
    node.start();
    node.waitForGenesis();
  }
}
