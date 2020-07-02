package tech.pegasys.teku.datastructures.forkchoice;

/**
 * Thrown when a checkpoint state cannot be generated because the checkpoint is invalid. Most
 * commonly because the blockRoot is for a block after the epoch start slot.
 */
public class InvalidCheckpointException extends RuntimeException {
  public InvalidCheckpointException(final Throwable cause) {
    super(cause);
  }
}
