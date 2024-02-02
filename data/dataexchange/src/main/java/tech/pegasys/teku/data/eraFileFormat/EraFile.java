/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.data.eraFileFormat;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class EraFile {
  private final ByteBuffer byteBuffer;
  private final RandomAccessFile file;
  private final FileChannel channel;
  private final long fileLength;

  private ReadSlotIndex stateIndices;
  private final String filename;

  public EraFile(final Path path) throws IOException {
    filename = path.getFileName().toString();
    file = new RandomAccessFile(path.toFile(), "r");
    channel = file.getChannel();
    fileLength = channel.size();

    final MappedByteBuffer mbb =
        channel.map(
            FileChannel.MapMode.READ_ONLY,
            0, // position
            file.length());
    byteBuffer = mbb.order(ByteOrder.LITTLE_ENDIAN);
  }

  public void readEraFile() {
    System.out.println("Reading Indices from ERA File " + filename);
    getStateIndices();
    getBlockIndices();
  }

  public void printStats() {
    int offset = 0;
    final Map<Bytes, Integer> entries = new HashMap<>();
    final Map<Bytes, Integer> entryBytes = new HashMap<>();
    while (offset < fileLength) {
      ReadEntry entry = new ReadEntry(byteBuffer, offset);
      offset += 8; // header
      offset += (int) entry.getDataSize();
      final Bytes key = Bytes.wrap(entry.getType());
      int i = entries.getOrDefault(key, 0);
      int size = entryBytes.getOrDefault(key, 0);
      entries.put(key, i + 1);
      entryBytes.put(key, size + (int) entry.getDataSize());
    }

    System.out.println("\nSummary Statistics");
    for (Bytes key : entries.keySet().stream().sorted().toList()) {
      final int count = entries.get(key);
      final int bytes = entryBytes.get(key);
      final double average = (double) bytes / (double) count;
      System.out.printf(
          "\ttype %s, bytes %10d, count %5d, average %12.02f%n",
          key.toUnprefixedHexString(), bytes, count, average);
    }
  }

  private void getStateIndices() {
    stateIndices = new ReadSlotIndex(byteBuffer, (int) fileLength);
    long recordStart = stateIndices.getRecordStart();
    if (!stateIndices.getEntry().isIndexType()) {
      throw new RuntimeException(
          String.format(
              " -- State e2store record type (i2) was not found at starting position "
                  + recordStart));
    }
    System.out.println("\tState slot: " + stateIndices.getStartSlot());
    System.out.println("\tState index start: " + recordStart);
    System.out.println("\tOffsets: " + stateIndices.getSlotOffsets());
  }

  private void getBlockIndices() {
    if (stateIndices.getStartSlot() > 0L) {
      ReadSlotIndex blockIndices =
          new ReadSlotIndex(byteBuffer, (int) stateIndices.getRecordStart());
      System.out.println("\tCount of blocks: " + blockIndices.getCount());
      System.out.println("\tBlock start slot: " + blockIndices.getStartSlot());
      System.out.println("\tOffsets: " + blockIndices.getSlotOffsets().size());
    } else {
      System.out.println("Slot 0");
    }
  }

  final void close() throws IOException {
    channel.close();
    file.close();
  }

  public static void main(String[] args) throws IOException {
    final Path p = Path.of(args[0]);
    final EraFile f = new EraFile(p);
    f.readEraFile();
    f.printStats();
    f.close();
  }
}
