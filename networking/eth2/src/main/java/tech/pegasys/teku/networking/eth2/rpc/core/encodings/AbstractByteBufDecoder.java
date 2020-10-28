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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Optional;

/**
 * Abstract {@link ByteBufDecoder} implementation which handles unprocessed {@link ByteBuf}s
 *
 * <p>This class is a standalone analog of Netty {@link io.netty.handler.codec.ByteToMessageDecoder}
 *
 * <p>This class is NOT thread safe. Calls should either be synchronized on a higher level or
 * performed from a single thread.
 */
public abstract class AbstractByteBufDecoder<TMessage, TException extends Exception>
    implements ByteBufDecoder<TMessage, TException> {

  private CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();
  private boolean closed = false;

  @Override
  public Optional<TMessage> decodeOneMessage(ByteBuf in) throws TException {
    if (!in.isReadable()) {
      return Optional.empty();
    }
    assertNotClosed();

    compositeByteBuf.addComponent(true, in.retainedSlice());
    try {
      Optional<TMessage> outBuf;
      while (true) {
        int readerIndex = compositeByteBuf.readerIndex();
        outBuf = decodeOneImpl(compositeByteBuf);
        if (outBuf.isPresent()
            || readerIndex == compositeByteBuf.readerIndex()
            || compositeByteBuf.readableBytes() == 0) {
          break;
        }
      }
      if (outBuf.isPresent()) {
        in.skipBytes(in.readableBytes() - compositeByteBuf.readableBytes());
        compositeByteBuf.release();
        compositeByteBuf = Unpooled.compositeBuffer();
      } else {
        in.skipBytes(in.readableBytes());
      }
      return outBuf;
    } catch (Throwable t) {
      close();
      throw t;
    }
  }

  @Override
  public void complete() throws TException {
    assertNotClosed();
    try {
      if (compositeByteBuf.isReadable()) {
        throwUnprocessedDataException(compositeByteBuf.readableBytes());
      }
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      compositeByteBuf.release();
    }
  }

  private void assertNotClosed() {
    if (closed) {
      throw new IllegalStateException("Trying to reuse disposed decoder instance");
    }
  }

  protected abstract void throwUnprocessedDataException(int dataLeft) throws TException;

  /**
   * Decodes one message if the full data is available in the buffer
   *
   * <p>If the full data is not available then return {@code empty} and left buffer reader index
   * intact
   *
   * <p>If a message is read then the buffer reader position should be positioned after the end of
   * message data
   */
  protected abstract Optional<TMessage> decodeOneImpl(ByteBuf in) throws TException;
}
