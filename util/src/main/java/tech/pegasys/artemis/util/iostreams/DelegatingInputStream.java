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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;

public class DelegatingInputStream extends InputStream {

  final InputStream wrapped;

  public DelegatingInputStream(final InputStream wrapped) {
    this.wrapped = wrapped;
  }

  @Override
  public int read() throws IOException {
    return wrapped.read();
  }

  @Override
  public int read(@NotNull final byte[] b) throws IOException {
    return wrapped.read(b);
  }

  @Override
  public int read(@NotNull final byte[] b, final int off, final int len) throws IOException {
    return wrapped.read(b, off, len);
  }

  @Override
  public byte[] readAllBytes() throws IOException {
    return wrapped.readAllBytes();
  }

  @Override
  public byte[] readNBytes(final int len) throws IOException {
    return wrapped.readNBytes(len);
  }

  @Override
  public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
    return wrapped.readNBytes(b, off, len);
  }

  @Override
  public long skip(final long n) throws IOException {
    return wrapped.skip(n);
  }

  @Override
  public int available() throws IOException {
    return wrapped.available();
  }

  @Override
  public void close() throws IOException {
    wrapped.close();
  }

  @Override
  public void mark(final int readlimit) {
    wrapped.mark(readlimit);
  }

  @Override
  public void reset() throws IOException {
    wrapped.reset();
  }

  @Override
  public boolean markSupported() {
    return wrapped.markSupported();
  }

  @Override
  public long transferTo(final OutputStream out) throws IOException {
    return wrapped.transferTo(out);
  }
}
