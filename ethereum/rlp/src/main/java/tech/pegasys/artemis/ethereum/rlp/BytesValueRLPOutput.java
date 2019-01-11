/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.ethereum.rlp;

import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.MutableBytesValue;

/** An {@link RLPOutput} that writes RLP encoded data to a {@link BytesValue}. */
public class BytesValueRLPOutput extends AbstractRLPOutput {
  /**
   * Computes the final encoded data.
   *
   * @return A value containing the data written to this output RLP-encoded.
   */
  public BytesValue encoded() {
    int size = encodedSize();
    if (size == 0) {
      return BytesValue.EMPTY;
    }

    MutableBytesValue output = MutableBytesValue.create(size);
    writeEncoded(output);
    return output;
  }
}
