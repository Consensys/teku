package tech.pegasys.artemis.storage.events;

import java.util.Optional;

public class StoreDiskUpdateCompleteEvent {
  private final long transactionId;
  private final Optional<RuntimeException> error;

  public StoreDiskUpdateCompleteEvent(final long transactionId, final Optional<RuntimeException> error) {
    this.transactionId = transactionId;
    this.error = error;
  }

  public long getTransactionId() {
    return transactionId;
  }

  public Optional<RuntimeException> getError() {
    return error;
  }
}
