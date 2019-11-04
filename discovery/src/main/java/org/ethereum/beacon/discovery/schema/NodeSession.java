/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.ethereum.beacon.discovery.schema;

import static org.ethereum.beacon.discovery.task.TaskStatus.AWAIT;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.storage.AuthTagRepository;
import org.ethereum.beacon.discovery.storage.NodeBucket;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeTable;
import org.ethereum.beacon.discovery.task.TaskStatus;
import org.ethereum.beacon.discovery.task.TaskType;

// import org.ethereum.beacon.util.ExpirationScheduler;

// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.bytes.Bytes;

/**
 * Stores session status and all keys for discovery session between us (homeNode) and the other node
 */
public class NodeSession {
  public static final int NONCE_SIZE = 12;
  public static final int REQUEST_ID_SIZE = 8;
  private static final Logger logger = LogManager.getLogger(NodeSession.class);
  private static final int CLEANUP_DELAY_SECONDS = 60;
  private final NodeRecord nodeRecord;
  private final NodeRecord homeNodeRecord;
  private final Bytes homeNodeId;
  private final AuthTagRepository authTagRepo;
  private final NodeTable nodeTable;
  private final NodeBucketStorage nodeBucketStorage;
  private final Consumer<Packet> outgoing;
  private final Random rnd;
  private SessionStatus status = SessionStatus.INITIAL;
  private Bytes idNonce;
  private Bytes initiatorKey;
  private Bytes recipientKey;
  private Map<Bytes, RequestInfo> requestIdStatuses = new ConcurrentHashMap<>();
  private ExpirationScheduler<Bytes> requestExpirationScheduler =
      new ExpirationScheduler<>(CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
  private CompletableFuture<Void> completableFuture = null;
  private Bytes staticNodeKey;

  public NodeSession(
      NodeRecord nodeRecord,
      NodeRecord homeNodeRecord,
      Bytes staticNodeKey,
      NodeTable nodeTable,
      NodeBucketStorage nodeBucketStorage,
      AuthTagRepository authTagRepo,
      Consumer<Packet> outgoing,
      Random rnd) {
    this.nodeRecord = nodeRecord;
    this.outgoing = outgoing;
    this.authTagRepo = authTagRepo;
    this.nodeTable = nodeTable;
    this.nodeBucketStorage = nodeBucketStorage;
    this.homeNodeRecord = homeNodeRecord;
    this.staticNodeKey = staticNodeKey;
    this.homeNodeId = homeNodeRecord.getNodeId();
    this.rnd = rnd;
  }

  public NodeRecord getNodeRecord() {
    return nodeRecord;
  }

  private void completeConnectFuture() {
    if (completableFuture != null) {
      completableFuture.complete(null);
      completableFuture = null;
    }
  }

  public synchronized void sendOutgoing(Packet packet) {
    logger.trace(() -> String.format("Sending outgoing packet %s in session %s", packet, this));
    outgoing.accept(packet);
  }

  /**
   * Creates object with request information: requestId etc, RequestInfo, designed to maintain
   * request status and its changes. Also stores info in session repository to track related
   * messages.
   *
   * <p>The value selected as request ID must allow for concurrent conversations. Using a timestamp
   * can result in parallel conversations with the same id, so this should be avoided. Request IDs
   * also prevent replay of responses. Using a simple counter would be fine if the implementation
   * could ensure that restarts or even re-installs would increment the counter based on previously
   * saved state in all circumstances. The easiest to implement is a random number.
   *
   * @param taskType Type of task, clarifies starting and reply message types
   * @param future Future to be fired when task is succcessfully completed or exceptionally break
   *     when its failed
   * @return info bundle.
   */
  public synchronized RequestInfo createNextRequest(
      TaskType taskType, CompletableFuture<Void> future) {
    byte[] requestId = new byte[REQUEST_ID_SIZE];
    rnd.nextBytes(requestId);
    Bytes wrappedId = Bytes.wrap(requestId);
    RequestInfo requestInfo = new GeneralRequestInfo(taskType, AWAIT, wrappedId, future);
    requestIdStatuses.put(wrappedId, requestInfo);
    requestExpirationScheduler.put(
        wrappedId,
        new Runnable() {
          @Override
          public void run() {
            logger.debug(
                () ->
                    String.format(
                        "Request %s expired for id %s in session %s: no reply",
                        requestInfo, wrappedId, this));
            requestIdStatuses.remove(wrappedId);
          }
        });
    return requestInfo;
  }

  public synchronized void updateRequestInfo(Bytes requestId, RequestInfo newRequestInfo) {
    RequestInfo oldRequestInfo = requestIdStatuses.remove(requestId);
    if (oldRequestInfo == null) {
      logger.debug(
          () ->
              String.format(
                  "An attempt to update requestId %s in session %s which does not exist",
                  requestId, this));
      return;
    }
    requestIdStatuses.put(requestId, newRequestInfo);
    requestExpirationScheduler.put(
        requestId,
        new Runnable() {
          @Override
          public void run() {
            logger.debug(
                String.format(
                    "Request %s expired for id %s in session %s: no reply",
                    newRequestInfo, requestId, this));
            requestIdStatuses.remove(requestId);
          }
        });
  }

  public synchronized void cancelAllRequests(String message) {
    logger.debug(() -> String.format("Cancelling all requests in session %s", this));
    Set<Bytes> requestIdsCopy = new HashSet<>(requestIdStatuses.keySet());
    requestIdsCopy.forEach(
        requestId -> {
          RequestInfo requestInfo = clearRequestId(requestId);
          requestInfo
              .getFuture()
              .completeExceptionally(
                  new RuntimeException(
                      String.format(
                          "Request %s cancelled due to reason: %s", requestInfo, message)));
        });
  }

  public synchronized Bytes generateNonce() {
    byte[] nonce = new byte[NONCE_SIZE];
    rnd.nextBytes(nonce);
    return Bytes.wrap(nonce);
  }

  public synchronized boolean isAuthenticated() {
    return SessionStatus.AUTHENTICATED.equals(status);
  }

  public void cleanup() {
    authTagRepo.expire(this);
  }

  public Optional<Bytes> getAuthTag() {
    return authTagRepo.getTag(this);
  }

  public void setAuthTag(Bytes authTag) {
    authTagRepo.put(authTag, this);
  }

  public Bytes getHomeNodeId() {
    return homeNodeId;
  }

  public Bytes getInitiatorKey() {
    return initiatorKey;
  }

  public void setInitiatorKey(Bytes initiatorKey) {
    this.initiatorKey = initiatorKey;
  }

  public Bytes getRecipientKey() {
    return recipientKey;
  }

  public void setRecipientKey(Bytes recipientKey) {
    this.recipientKey = recipientKey;
  }

  public synchronized void clearRequestId(Bytes requestId, TaskType taskType) {
    RequestInfo requestInfo = clearRequestId(requestId);
    requestInfo.getFuture().complete(null);
    assert taskType.equals(requestInfo.getTaskType());
  }

  private synchronized RequestInfo clearRequestId(Bytes requestId) {
    RequestInfo requestInfo = requestIdStatuses.remove(requestId);
    requestExpirationScheduler.cancel(requestId);
    return requestInfo;
  }

  public synchronized Optional<RequestInfo> getRequestId(Bytes requestId) {
    RequestInfo requestInfo = requestIdStatuses.get(requestId);
    return requestId == null ? Optional.empty() : Optional.of(requestInfo);
  }

  public synchronized Optional<RequestInfo> getFirstAwaitRequestInfo() {
    return requestIdStatuses.values().stream()
        .filter(requestInfo -> AWAIT.equals(requestInfo.getTaskStatus()))
        .findFirst();
  }

  public NodeTable getNodeTable() {
    return nodeTable;
  }

  public void putRecordInBucket(NodeRecordInfo nodeRecordInfo) {
    nodeBucketStorage.put(nodeRecordInfo);
  }

  public Optional<NodeBucket> getBucket(int index) {
    return nodeBucketStorage.get(index);
  }

  public synchronized Bytes getIdNonce() {
    return idNonce;
  }

  public synchronized void setIdNonce(Bytes idNonce) {
    this.idNonce = idNonce;
  }

  public NodeRecord getHomeNodeRecord() {
    return homeNodeRecord;
  }

  @Override
  public String toString() {
    return "NodeSession{"
        + "nodeRecord="
        + nodeRecord
        + ", homeNodeId="
        + homeNodeId
        + ", status="
        + status
        + '}';
  }

  public synchronized SessionStatus getStatus() {
    return status;
  }

  public synchronized void setStatus(SessionStatus newStatus) {
    logger.debug(
        () ->
            String.format(
                "Switching status of node %s from %s to %s", nodeRecord, status, newStatus));
    this.status = newStatus;
  }

  public Bytes getStaticNodeKey() {
    return staticNodeKey;
  }

  public enum SessionStatus {
    INITIAL, // other side is trying to connect, or we are initiating (before random packet is sent
    WHOAREYOU_SENT, // other side is initiator, we've sent whoareyou in response
    RANDOM_PACKET_SENT, // our node is initiator, we've sent random packet
    AUTHENTICATED
  }

  public interface RequestInfo {
    TaskType getTaskType();

    TaskStatus getTaskStatus();

    Bytes getRequestId();

    CompletableFuture<Void> getFuture();
  }

  public static class GeneralRequestInfo implements RequestInfo {
    private final TaskType taskType;
    private final TaskStatus taskStatus;
    private final Bytes requestId;
    private final CompletableFuture<Void> future;

    public GeneralRequestInfo(
        TaskType taskType, TaskStatus taskStatus, Bytes requestId, CompletableFuture<Void> future) {
      this.taskType = taskType;
      this.taskStatus = taskStatus;
      this.requestId = requestId;
      this.future = future;
    }

    @Override
    public TaskType getTaskType() {
      return taskType;
    }

    @Override
    public TaskStatus getTaskStatus() {
      return taskStatus;
    }

    @Override
    public Bytes getRequestId() {
      return requestId;
    }

    @Override
    public CompletableFuture<Void> getFuture() {
      return future;
    }

    @Override
    public String toString() {
      return "GeneralRequestInfo{"
          + "taskType="
          + taskType
          + ", taskStatus="
          + taskStatus
          + ", requestId="
          + requestId
          + '}';
    }
  }
}
