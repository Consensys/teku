/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

public class BeaconChainMethodIds {
  static final String STATUS = "/eth2/beacon_chain/req/status";
  static final String GOODBYE = "/eth2/beacon_chain/req/goodbye";
  static final String BEACON_BLOCKS_BY_ROOT = "/eth2/beacon_chain/req/beacon_blocks_by_root";
  static final String BEACON_BLOCKS_BY_RANGE = "/eth2/beacon_chain/req/beacon_blocks_by_range";
  static final String BEACON_BLOCK_AND_BLOBS_SIDECAR_BY_ROOT =
      "/eth2/beacon_chain/req/beacon_block_and_blobs_sidecar_by_root";
  static final String BLOBS_SIDECARS_BY_RANGE = "/eth2/beacon_chain/req/blobs_sidecars_by_range";
  static final String GET_METADATA = "/eth2/beacon_chain/req/metadata";
  static final String PING = "/eth2/beacon_chain/req/ping";

  public static String getMethodId(
      final String methodPrefix, final int version, final RpcEncoding encoding) {
    return methodPrefix + "/" + version + "/" + encoding.getName();
  }

  public static String getBlocksByRangeMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(BEACON_BLOCKS_BY_RANGE, version, encoding);
  }

  public static String getBlocksByRootMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(BEACON_BLOCKS_BY_ROOT, version, encoding);
  }

  public static String getBeaconBlockAndBlobsSidecarByRoot(
      final int version, final RpcEncoding encoding) {
    return getMethodId(BEACON_BLOCK_AND_BLOBS_SIDECAR_BY_ROOT, version, encoding);
  }

  public static String getBlobsSidecarsByRangeMethodId(
      final int version, final RpcEncoding encoding) {
    return getMethodId(BLOBS_SIDECARS_BY_RANGE, version, encoding);
  }

  public static String getStatusMethodId(final int version, final RpcEncoding encoding) {
    return getMethodId(STATUS, version, encoding);
  }

  public static int extractBeaconBlocksByRootVersion(final String methodId) {
    return extractVersion(methodId, BEACON_BLOCKS_BY_ROOT);
  }

  public static int extractBeaconBlocksByRangeVersion(final String methodId) {
    return extractVersion(methodId, BEACON_BLOCKS_BY_RANGE);
  }

  public static int extractGetMetadataVersion(final String methodId) {
    return extractVersion(methodId, GET_METADATA);
  }

  @VisibleForTesting
  static int extractVersion(final String methodId, final String methodPrefix) {
    final String versionAndEncoding = methodId.replace(methodPrefix + "/", "");
    final String version = versionAndEncoding.substring(0, versionAndEncoding.indexOf("/"));
    return Integer.parseInt(version, 10);
  }
}
