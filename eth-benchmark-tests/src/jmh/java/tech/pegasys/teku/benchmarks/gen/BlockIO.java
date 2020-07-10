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

package tech.pegasys.teku.benchmarks.gen;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;

/** Utility class to read/write SSZ serialized blocks */
public class BlockIO {

  public static class Reader
      implements AutoCloseable, Supplier<SignedBeaconBlock>, Iterable<SignedBeaconBlock> {

    private final ObjectInputStream inputStream;

    Reader(ObjectInputStream inputStream) {
      this.inputStream = inputStream;
    }

    @Override
    public void close() throws Exception {
      inputStream.close();
    }

    @Override
    public SignedBeaconBlock get() {
      try {
        int size = inputStream.readInt();
        byte[] bytes = new byte[size];
        inputStream.readFully(bytes);
        return SimpleOffsetSerializer.deserialize(Bytes.wrap(bytes), SignedBeaconBlock.class);
      } catch (Exception e) {
        return null;
      }
    }

    @NotNull
    @Override
    public Iterator<SignedBeaconBlock> iterator() {
      return Utils.fromSupplier(this);
    }

    @SuppressWarnings("EmptyCatch")
    public List<SignedBeaconBlock> readAll(int limit) {
      try {
        return StreamSupport.stream(spliterator(), false).limit(limit).collect(Collectors.toList());
      } finally {
        try {
          close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public static class Writer implements AutoCloseable, Consumer<SignedBeaconBlock> {
    private final ObjectOutputStream outputStream;

    Writer(ObjectOutputStream outputStream) {
      this.outputStream = outputStream;
    }

    @Override
    public void close() throws Exception {
      outputStream.close();
    }

    @Override
    public void accept(SignedBeaconBlock block) {
      try {
        Bytes bytes = SimpleOffsetSerializer.serialize(block);
        outputStream.writeInt(bytes.size());
        outputStream.write(bytes.toArrayUnsafe());
        outputStream.flush();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Writer createFileWriter(String outFile) {
    try {
      return new Writer(new ObjectOutputStream(new FileOutputStream(outFile)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Reader createResourceReader(String resourcePath) {
    try {
      return createReader(
          BlockIO.class.getResourceAsStream(resourcePath), resourcePath.endsWith(".gz"));
    } catch (Exception e) {
      throw new RuntimeException("Error opening resource " + resourcePath, e);
    }
  }

  public static Reader createFileReader(String inFile) {
    try {
      return createReader(new FileInputStream(inFile), inFile.endsWith(".gz"));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Error opening file " + inFile, e);
    }
  }

  public static Reader createReader(InputStream inputStream, boolean gzipped) {
    try {
      return new Reader(
          new ObjectInputStream(gzipped ? new GZIPInputStream(inputStream) : inputStream));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
