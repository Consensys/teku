package tech.pegasys.artemis.test.acceptance.dsl;

import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;

public class AcceptanceTestBase {

  private final SimpleHttpClient httpClient = new SimpleHttpClient();
  private final List<ArtemisNode> nodes = new ArrayList<>();

  @AfterEach
  final void shutdownNodes() {
    nodes.forEach(ArtemisNode::stop);
  }

  protected ArtemisNode createArtemisNode() {
    return new ArtemisNode(httpClient);
  }
}
