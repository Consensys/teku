package net.consensys.beaconchain.ethereum.rlp;

public class RLPException extends RuntimeException {
  public RLPException(String message) {
    this(message, null);
  }

  RLPException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
