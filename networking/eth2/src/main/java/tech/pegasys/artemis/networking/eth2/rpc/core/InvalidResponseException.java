package tech.pegasys.artemis.networking.eth2.rpc.core;

public class InvalidResponseException extends RuntimeException {

  public InvalidResponseException(final String message) {
    super(message);
  }

  public InvalidResponseException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
