package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import io.libp2p.core.Connection;
import io.libp2p.core.ConnectionHandler;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.artemis.util.alogger.ALogger;

public class LibP2PPeerManager implements ConnectionHandler {
  private final ALogger LOG = new ALogger(LibP2PPeerManager.class.getName());

  @Override
  public void handleConnection(@NotNull final Connection connection) {
    LOG.log(Level.DEBUG, "Got new connection from " + connection.getSecureSession().getRemoteId());
    if (connection.isInitiator()) {
      // TODO: Send hello
    }
  }
}
