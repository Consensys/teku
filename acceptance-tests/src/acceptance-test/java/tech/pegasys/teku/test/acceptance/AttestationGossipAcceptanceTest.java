package tech.pegasys.teku.test.acceptance;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuNode;

public class AttestationGossipAcceptanceTest extends AcceptanceTestBase {
  @Test
  public void shouldFinalizeWithTwoNodes() throws Exception {
    final TekuNode node1 = createTekuNode(config ->
            config.withRealNetwork()
                    .withInteropValidators(0, 32)
    );

    node1.start();
    final UInt64 genesisTime = node1.getGenesisTime();

    final TekuNode node2 = createTekuNode(config ->
            config
                    .withGenesisTime(genesisTime.intValue())
                    .withRealNetwork()
                    .withPeers(node1)
                    .withInteropValidators(33, 30));
    node2.start();

    node2.waitForAttestationBeingGossiped(32, 64);
  }
}
