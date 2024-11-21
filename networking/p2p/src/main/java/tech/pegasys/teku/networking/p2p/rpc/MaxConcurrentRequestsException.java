package tech.pegasys.teku.networking.p2p.rpc;

public class MaxConcurrentRequestsException extends RuntimeException {

  public MaxConcurrentRequestsException(final String message) {
    super(message);
  }
}
