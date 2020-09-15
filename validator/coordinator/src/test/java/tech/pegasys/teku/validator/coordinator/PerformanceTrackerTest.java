package tech.pegasys.teku.validator.coordinator;

import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.performance.PerformanceTracker;

import static org.mockito.Mockito.mock;

public class PerformanceTrackerTest {

  private RecentChainData recentChainData = mock(RecentChainData.class);
  private PerformanceTracker performanceTracker = new PerformanceTracker(recentChainData);


}
