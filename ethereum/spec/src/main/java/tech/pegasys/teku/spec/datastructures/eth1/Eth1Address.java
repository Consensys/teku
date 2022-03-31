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
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class Eth1Address extends Bytes20 {
  private static final DeserializableTypeDefinition<Eth1Address> ETH1ADDRESS_TYPE =
      DeserializableTypeDefinition.string(Eth1Address.class)
          .formatter(Eth1Address::toHexString)
          .parser(Eth1Address::fromHexString)
          .example("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6")
          .description("Hex encoded deposit contract address with 0x prefix")
          .format("byte")
          .build();

  public static final Eth1Address ZERO = new Eth1Address(Bytes.wrap(new byte[SIZE]));

  public Eth1Address(final Bytes bytes) {
    super(bytes);
  }

  public static Eth1Address fromHexString(String value) {
    return new Eth1Address(Bytes.fromHexString(value));
  }

  public static DeserializableTypeDefinition<Eth1Address> getJsonTypeDefinition() {
    return ETH1ADDRESS_TYPE;
  }
}
