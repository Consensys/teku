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

package tech.pegasys.teku.spec.datastructures.eth1;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes20;

public class Eth1Address extends Bytes20 {
  public static final Eth1Address ZERO = new Eth1Address(Bytes.wrap(new byte[SIZE]));

  public Eth1Address(final Bytes bytes) {
    super(bytes);
  }

  public static Eth1Address fromHexString(String value) {
    return new Eth1Address(Bytes.fromHexString(value));
  }
}
