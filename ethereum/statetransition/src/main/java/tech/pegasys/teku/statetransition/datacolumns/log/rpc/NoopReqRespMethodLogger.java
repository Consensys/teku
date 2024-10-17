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

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

class NoopReqRespMethodLogger<TRequest, TResponse>
    implements ReqRespMethodLogger<TRequest, TResponse> {

  @Override
  public ReqRespResponseLogger<TResponse> onInboundRequest(
      LoggingPeerId fromPeer, TRequest request) {
    return noopResponseLogger();
  }

  @Override
  public ReqRespResponseLogger<TResponse> onOutboundRequest(
      LoggingPeerId toPeer, TRequest request) {
    return noopResponseLogger();
  }

  static <TResponse> ReqRespResponseLogger<TResponse> noopResponseLogger() {
    return new ReqRespResponseLogger<>() {
      @Override
      public void onNextItem(TResponse s) {}

      @Override
      public void onComplete() {}

      @Override
      public void onError(Throwable error) {}
    };
  }
}
