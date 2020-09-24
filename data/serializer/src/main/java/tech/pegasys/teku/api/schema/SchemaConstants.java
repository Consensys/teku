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

package tech.pegasys.teku.api.schema;

public class SchemaConstants {
  public static final String DESCRIPTION_BYTES32 = "Bytes32 hexadecimal";
  public static final String DESCRIPTION_BYTES4 = "Bytes4 hexadecimal";
  public static final String DESCRIPTION_BYTES96 = "Bytes96 hexadecimal";
  public static final String DESCRIPTION_BYTES48 = "Bytes48 hexadecimal";
  public static final String DESCRIPTION_BYTES_SSZ = "SSZ hexadecimal";

  public static final String PATTERN_UINT64 = "^0-9+$";
  public static final String PATTERN_PUBKEY = "^0x[a-fA-F0-9]{96}$";
  public static final String PATTERN_BYTES4 = "^0x[a-fA-F0-9]{8}$";
  public static final String PATTERN_BYTES32 = "^0x[a-fA-F0-9]{64}$";

  public static final String EXAMPLE_PUBKEY =
      "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a";
  public static final String EXAMPLE_BYTES32 =
      "0xcf8e0d4e9587369b2301d0790347320302cc0943d5a1884560367e8208d920f2";
  public static final String EXAMPLE_UINT64 = "1";
}
