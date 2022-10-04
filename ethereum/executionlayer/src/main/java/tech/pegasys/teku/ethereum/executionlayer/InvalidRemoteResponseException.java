package tech.pegasys.teku.ethereum.executionlayer;

public class InvalidRemoteResponseException extends RuntimeException {

  public InvalidRemoteResponseException(final String message) {
    super(message);
  }
}
