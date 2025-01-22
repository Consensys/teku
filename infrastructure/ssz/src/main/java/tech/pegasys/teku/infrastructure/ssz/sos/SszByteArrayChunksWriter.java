/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.infrastructure.ssz.sos;

import java.util.Arrays;
import java.util.List;

public class SszByteArrayChunksWriter implements SszWriter {
  private final byte[][] chunks;
  private final int maxChunkSize;
  private final int maxChunks;

  private int lastChunkIndex;
  private int size = 0;

  public SszByteArrayChunksWriter(final int maxSize, final int maxChunkSize) {
    this.maxChunks = (maxSize + maxChunkSize - 1) / maxChunkSize;
    this.chunks = new byte[maxChunks][];
    this.maxChunkSize = maxChunkSize;
  }

  @Override
  public void write(final byte[] bytes, final int offset, final int length) {
    final int chunk = this.size / maxChunkSize;
    final int chunkOffset = this.size % maxChunkSize;
    final byte[] chunkData = getChunk(chunk);

    if (chunkOffset + length > maxChunkSize) {
      final int lengthToFillCurrentChunk = maxChunkSize - chunkOffset;
      System.arraycopy(bytes, offset, chunkData, chunkOffset, lengthToFillCurrentChunk);
      this.size += lengthToFillCurrentChunk;

      write(bytes, offset + lengthToFillCurrentChunk, length - lengthToFillCurrentChunk);
    } else {
      System.arraycopy(bytes, offset, chunkData, chunkOffset, length);
      this.size += length;
    }
  }

  private byte[] getChunk(final int chunk) {
    if (chunk >= maxChunks) {
      throw new IndexOutOfBoundsException(
          "Chunk index out of bounds: " + chunk + ", max chunks: " + maxChunks);
    }
    byte[] chunkData = chunks[chunk];
    if (chunkData == null) {
      chunkData = new byte[maxChunkSize];
      chunks[chunk] = chunkData;
      lastChunkIndex = chunk;
    }
    return chunkData;
  }

  public List<byte[]> getChunks() {
    int lastChunkSize = size % maxChunkSize;
    if (lastChunkSize != 0) {
      byte[] lastChunk = chunks[lastChunkIndex];
      byte[] lastChunkTrimmed = Arrays.copyOf(lastChunk, lastChunkSize);
      chunks[lastChunkIndex] = lastChunkTrimmed;
    }

    return Arrays.stream(chunks).limit(lastChunkIndex + 1).toList();
  }
}
