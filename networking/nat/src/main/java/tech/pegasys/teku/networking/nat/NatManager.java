/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.networking.nat;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.async.FutureUtil.ignoreFuture;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceImpl;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteDeviceIdentity;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UnsignedIntegerFourBytes;
import org.jupnp.model.types.UnsignedIntegerTwoBytes;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.jupnp.support.igd.callback.GetExternalIP;
import org.jupnp.support.model.PortMapping;
import tech.pegasys.teku.infrastructure.async.FutureUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.service.serviceutils.Service;

@SuppressWarnings("unchecked")
public class NatManager extends Service {
  private static final Logger LOG = LogManager.getLogger();

  static final String SERVICE_TYPE_WAN_IP_CONNECTION = "WANIPConnection";

  private final UpnpService upnpService;
  private final RegistryListener registryListener;
  private final SafeFuture<String> externalIpQueryFuture = new SafeFuture<>();
  private final SafeFuture<RemoteService> wanIpConnection = new SafeFuture<>();
  private final Queue<NatPortMapping> forwardedPorts = new ConcurrentLinkedQueue<>();
  private Optional<String> discoveredOnLocalAddress = Optional.empty();

  public NatManager(final NatMethod natMethod) {
    // this(new UpnpServiceImpl(new DefaultUpnpServiceConfiguration()));

    // Workaround for an issue in the jupnp library: the ExecutorService used misconfigures
    // its ThreadPoolExecutor, causing it to only launch a single thread. This prevents any work
    // from getting done (effectively a deadlock). The issue is fixed here:
    //   https://github.com/jupnp/jupnp/pull/117
    // However, this fix has not made it into any releases yet.
    // TODO: once a new release is available, remove this @Override
    this(new UpnpServiceImpl(new TekuNatServiceConfiguration()), natMethod);
  }

  /**
   * Constructor
   *
   * @param service is the desired instance of UpnpService.
   */
  NatManager(final UpnpService service, final NatMethod natMethod) {
    upnpService = service;

    // registry listener to observe new devices and look for specific services
    registryListener =
        new TekuRegistryListener() {
          @Override
          public void remoteDeviceAdded(final Registry registry, final RemoteDevice device) {
            LOG.debug("UPnP Device discovered: " + device.getDetails().getFriendlyName());
            inspectDeviceRecursive(device);
          }
        };
  }

  private void inspectDeviceRecursive(final RemoteDevice device) {
    for (RemoteService service : device.getServices()) {
      String serviceType = service.getServiceType().getType();
      if (serviceType.equalsIgnoreCase(SERVICE_TYPE_WAN_IP_CONNECTION)) {
        wanIpConnection.complete(service);
      }
    }
    for (RemoteDevice subDevice : device.getEmbeddedDevices()) {
      inspectDeviceRecursive(subDevice);
    }
  }

  @Override
  public SafeFuture<Void> doStart() {
    LOG.info("Starting UPnP Service");

    upnpService.startup();
    upnpService.getRegistry().addListener(registryListener);
    initiateExternalIpQuery();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<Void> doStop() {
    return releaseAllPortForwards()
        .orTimeout(3, TimeUnit.SECONDS)
        .exceptionally(
            error -> {
              LOG.debug("Failed to release port forwards", error);
              return null;
            })
        .alwaysRun(
            () -> {
              wanIpConnection.cancel(true);

              upnpService.getRegistry().removeListener(registryListener);
              upnpService.shutdown();
            });
  }

  private void initiateExternalIpQuery() {
    wanIpConnection
        .thenAccept(
            service -> {

              // our query, which will be handled asynchronously by the jupnp library
              GetExternalIP callback =
                  new GetExternalIP(service) {

                    /**
                     * Override the success(ActionInvocation) version of success so that we can take
                     * a peek at the network interface that we discovered this on.
                     *
                     * <p>Because the underlying jupnp library omits generics info in this method
                     * signature, we must too when we override it.
                     */
                    @Override
                    @SuppressWarnings("rawtypes")
                    public void success(final ActionInvocation invocation) {
                      RemoteService service = (RemoteService) invocation.getAction().getService();
                      RemoteDevice device = service.getDevice();
                      RemoteDeviceIdentity identity = device.getIdentity();

                      discoveredOnLocalAddress =
                          Optional.of(identity.getDiscoveredOnLocalAddress().getHostAddress());

                      super.success(invocation);
                    }

                    @Override
                    protected void success(final String result) {

                      LOG.info(
                          "External IP address {} detected for internal address {}",
                          result,
                          discoveredOnLocalAddress.get());

                      externalIpQueryFuture.complete(result);
                    }

                    /**
                     * Because the underlying jupnp library omits generics info in this method
                     * signature, we must too when we override it.
                     */
                    @Override
                    @SuppressWarnings("rawtypes")
                    public void failure(
                        final ActionInvocation invocation,
                        final UpnpResponse operation,
                        final String msg) {
                      externalIpQueryFuture.completeExceptionally(new Exception(msg));
                    }
                  };
              FutureUtil.ignoreFuture(upnpService.getControlPoint().execute(callback));
            })
        .finish(error -> LOG.warn("Failed to retrieve external ip address", error));
  }

  @VisibleForTesting
  CompletableFuture<RemoteService> getWANIPConnectionService() {
    checkState(isRunning(), "Cannot call getWANIPConnectionService() when in stopped state");
    return wanIpConnection;
  }

  private SafeFuture<Void> releaseAllPortForwards() {
    // if we haven't observed the WANIPConnection service yet, we should have no port forwards to
    // release
    if (!wanIpConnection.isDone()) {
      LOG.debug("Ports had not completed setting up, will not be able to disconnect.");
      return SafeFuture.COMPLETE;
    }

    if (forwardedPorts.isEmpty()) {
      LOG.debug("Port list is empty.");
      return SafeFuture.COMPLETE;
    } else {
      LOG.debug("Have {} ports to close", forwardedPorts.size());
    }
    RemoteService service = wanIpConnection.join();

    List<SafeFuture<Void>> futures = new ArrayList<>();

    while (!forwardedPorts.isEmpty()) {
      final NatPortMapping portMapping = forwardedPorts.remove();
      LOG.debug(
          "Releasing port forward for {} {} -> {}",
          portMapping.getProtocol(),
          portMapping.getInternalPort(),
          portMapping.getExternalPort());

      TekuPortMappingDelete callback =
          new TekuPortMappingDelete(service, toJupnpPortMapping(portMapping));

      FutureUtil.ignoreFuture(upnpService.getControlPoint().execute(callback));

      futures.add(callback.getFuture());
    }

    // return a future that completes successfully only when each of our port delete requests
    // complete
    return SafeFuture.allOf(futures.toArray(new SafeFuture<?>[0]));
  }

  public void requestPortForward(
      final int port, final NetworkProtocol protocol, final NatServiceType serviceType) {
    checkState(isRunning(), "Cannot call requestPortForward() when in stopped state");
    checkArgument(port != 0, "Cannot map to internal port zero.");
    this.requestPortForward(
            new PortMapping(
                true,
                new UnsignedIntegerFourBytes(0),
                null,
                new UnsignedIntegerTwoBytes(port),
                new UnsignedIntegerTwoBytes(port),
                null,
                toJupnpProtocol(protocol),
                serviceType.getValue()))
        .finish(error -> LOG.debug("Failed to forward port ", error));
  }

  private SafeFuture<Void> requestPortForward(final PortMapping portMapping) {
    return externalIpQueryFuture.thenCompose(
        address -> {
          // note that this future is a dependency of externalIpQueryFuture, so it must be completed
          // by now
          RemoteService service = wanIpConnection.join();

          // at this point, we should have the local address we discovered the IGD on,
          // so we can prime the NewInternalClient field if it was omitted
          if (null == portMapping.getInternalClient()) {
            portMapping.setInternalClient(discoveredOnLocalAddress.get());
          }

          // our query, which will be handled asynchronously by the jupnp library
          TekuPortMappingAdd callback = new TekuPortMappingAdd(service, portMapping);

          LOG.debug(
              "Requesting port forward for {} {} -> {}",
              portMapping.getProtocol(),
              portMapping.getInternalPort(),
              portMapping.getExternalPort());

          ignoreFuture(upnpService.getControlPoint().execute(callback));
          return callback
              .getFuture()
              .thenCompose(
                  port -> {
                    forwardedPorts.add(port);
                    return SafeFuture.COMPLETE;
                  })
              .exceptionallyCompose(
                  error -> {
                    LOG.debug("Failed to forward port", error);
                    return SafeFuture.failedFuture(error);
                  });
        });
  }

  private PortMapping.Protocol toJupnpProtocol(final NetworkProtocol protocol) {
    switch (protocol) {
      case UDP:
        return PortMapping.Protocol.UDP;
      case TCP:
        return PortMapping.Protocol.TCP;
    }
    return null;
  }

  private PortMapping toJupnpPortMapping(final NatPortMapping natPortMapping) {
    return new PortMapping(
        true,
        new UnsignedIntegerFourBytes(0),
        null,
        new UnsignedIntegerTwoBytes(natPortMapping.getExternalPort()),
        new UnsignedIntegerTwoBytes(natPortMapping.getInternalPort()),
        null,
        toJupnpProtocol(natPortMapping.getProtocol()),
        natPortMapping.getNatServiceType().getValue());
  }
}
