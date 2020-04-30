/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.util.iostreams;

import static tech.pegasys.artemis.util.iostreams.IOStreamConstants.END_OF_STREAM;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.util.bytes.ByteUtil;

/** An InputStream implementation that can be tracked, inspected, and directly written to. */
public class MockInputStream extends InputStream {
  private boolean closed = false;

  private final Deque<Byte> unconsumedBytes = new ArrayDeque<>();
  private final Deque<Byte> consumedBytes = new ArrayDeque<>();

  private CompletableFuture<Void> nextReadResultReady = new CompletableFuture<>();
  private final AtomicBoolean isWaitingOnNextByte = new AtomicBoolean(false);

  @Override
  public int read() throws IOException {
    assertOpen();
    final CompletableFuture<Void> nextResultReady;
    synchronized (this) {
      final OptionalInt nextResult = readNextByte();
      if (nextResult.isPresent()) {
        return nextResult.getAsInt();
      }
      nextResultReady = this.nextReadResultReady;
    }

    isWaitingOnNextByte.set(true);
    nextResultReady.join();
    isWaitingOnNextByte.set(false);

    return forceReadNextByte();
  }

  @Override
  public synchronized byte[] readNBytes(int len) throws IOException {
    assertOpen();
    final List<Byte> bytes = new ArrayList<>();
    while (bytes.size() < len) {
      final int nextByte = read();
      if (nextByte == END_OF_STREAM) {
        // End of stream
        break;
      }
      bytes.add((byte) nextByte);
    }
    return bytesToArray(bytes);
  }

  @Override
  public int read(final byte[] b, final int off, final int len) throws IOException {
    final byte[] bytesToCopy = readNBytes(len);
    if (bytesToCopy.length == 0) {
      return END_OF_STREAM;
    }

    System.arraycopy(bytesToCopy, 0, b, off, bytesToCopy.length);
    return bytesToCopy.length;
  }

  @Override
  public int available() {
    return unconsumedBytes.size();
  }

  @Override
  public synchronized void close() {
    closed = true;
    if (unconsumedBytes.isEmpty()) {
      nextReadResultReady.complete(null);
    }
  }

  public synchronized void deliverBytes(final Bytes bytes) throws IOException {
    if (closed) {
      throw new IOException("Attempt to write bytes to closed stream");
    }
    final boolean pendingBytesWasEmpty = unconsumedBytes.size() == 0;
    final byte[] byteArray = bytes.toArrayUnsafe();
    for (byte b : byteArray) {
      unconsumedBytes.add(b);
    }

    if (pendingBytesWasEmpty) {
      nextReadResultReady.complete(null);
      nextReadResultReady = new CompletableFuture<>();
    }
  }

  public boolean isWaitingOnNextByteToBeDelivered() {
    return isWaitingOnNextByte.get();
  }

  public synchronized int countBytesRead() {
    return consumedBytes.size();
  }

  public synchronized int countUnconsumedBytes() {
    return unconsumedBytes.size();
  }

  public synchronized Bytes getConsumedBytes() {
    final byte[] bytes = bytesToArray(consumedBytes);
    return Bytes.wrap(bytes);
  }

  public synchronized Bytes getUnconsumedBytes() {
    final byte[] bytes = bytesToArray(unconsumedBytes);
    return Bytes.wrap(bytes);
  }

  private final byte[] bytesToArray(final Collection<Byte> bytes) {
    return com.google.common.primitives.Bytes.toArray(bytes);
  }

  private int forceReadNextByte() {
    return readNextByte()
        .orElseThrow(() -> new IllegalStateException("Failed to read next byte. This is a bug."));
  }

  private synchronized OptionalInt readNextByte() {
    if (!unconsumedBytes.isEmpty()) {
      final Byte nextByte = unconsumedBytes.remove();
      consumedBytes.add(nextByte);
      return OptionalInt.of(ByteUtil.toUnsignedInt(nextByte));
    } else if (closed) {
      return OptionalInt.of(END_OF_STREAM);
    }
    return OptionalInt.empty();
  }

  private synchronized void assertOpen() throws IOException {
    if (closed) {
      throw new IOException("Stream closed");
    }
  }
}
