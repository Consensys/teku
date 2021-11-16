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

package tech.pegasys.teku.services;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceFacade;

public abstract class ServiceController extends Service implements ServiceControllerFacade {

  protected final List<Service> services = new ArrayList<>();

  @Override
  protected SafeFuture<?> doStart() {
    final Iterator<Service> iterator = services.iterator();
    SafeFuture<?> startupFuture = iterator.next().start();
    while (iterator.hasNext()) {
      final Service nextService = iterator.next();
      startupFuture = startupFuture.thenCompose(__ -> nextService.start());
    }
    return startupFuture;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(services.stream().map(Service::stop).toArray(SafeFuture[]::new));
  }

  @Override
  public List<? extends ServiceFacade> getServices() {
    return services;
  }
}
