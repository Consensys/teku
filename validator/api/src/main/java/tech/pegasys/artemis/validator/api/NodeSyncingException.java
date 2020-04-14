package tech.pegasys.artemis.validator.api;

import tech.pegasys.artemis.util.async.SafeFuture;

/** Indicates that the request couldn't be completed because the node is currently syncing. */
public class NodeSyncingException extends RuntimeException {

  public static <T> SafeFuture<T> failedFuture() {
    return SafeFuture.failedFuture(new NodeSyncingException());
  }
}
