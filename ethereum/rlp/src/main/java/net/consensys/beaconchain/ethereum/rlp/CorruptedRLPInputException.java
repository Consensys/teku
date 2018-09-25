package net.consensys.beaconchain.ethereum.rlp;

/**
 * Exception thrown if an RLP input is corrupted and cannot be decoded properly.
 */
public class CorruptedRLPInputException extends RLPException {
  CorruptedRLPInputException(String message) {
    super(message);
  }
}
