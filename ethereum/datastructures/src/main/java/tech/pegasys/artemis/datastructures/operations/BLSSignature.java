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

package tech.pegasys.artemis.datastructures.operations;

import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.bytes.Bytes48;
import net.consensys.cava.ssz.SSZ;
import tech.pegasys.artemis.util.bls.Signature;

public final class BLSSignature extends Signature {

  public BLSSignature(Bytes48 c0, Bytes48 c1) {
    super(c0, c1);
  }

  public static BLSSignature fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new BLSSignature(Bytes48.wrap(reader.readBytes()), Bytes48.wrap(reader.readBytes())));
  }

  public Bytes toBytes() {
    return SSZ.encode(
        writer -> {
          writer.writeBytes(c0);
          writer.writeBytes(c1);
        });
  }
}
