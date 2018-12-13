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

package net.consensys.artemis.ethereum.rlp;

import net.consensys.artemis.util.bytes.Bytes32;
import net.consensys.artemis.util.bytes.BytesValue;
import net.consensys.artemis.util.bytes.BytesValues;

import java.math.BigInteger;

/**
 * An {@link RLPInput} that reads RLP encoded data from a {@link BytesValue}.
 */
public class BytesValueRLPInput extends AbstractRLPInput {

  // The RLP encoded data.
  private final BytesValue value;

  public BytesValueRLPInput(BytesValue value, boolean lenient) {
    super(lenient);
    this.value = value;
    init(value.size(), true);
  }

  @Override
  protected byte inputByte(long offset) {
    return value.get(Math.toIntExact(offset));
  }

  @Override
  protected BytesValue inputSlice(long offset, int length) {
    return value.slice(Math.toIntExact(offset), length);
  }

  @Override
  protected Bytes32 inputSlice32(long offset) {
    return Bytes32.wrap(value, Math.toIntExact(offset));
  }

  @Override
  protected String inputHex(long offset, int length) {
    return value.slice(Math.toIntExact(offset), length).toString().substring(2);
  }

  @Override
  protected BigInteger getUnsignedBigInteger(long offset, int length) {
    return BytesValues.asUnsignedBigInteger(value.slice(Math.toIntExact(offset), length));
  }

  @Override
  protected int getInt(long offset) {
    return value.getInt(Math.toIntExact(offset));
  }

  @Override
  protected long getLong(long offset) {
    return value.getLong(Math.toIntExact(offset));
  }

  @Override
  public BytesValue raw() {
    return value;
  }
}
