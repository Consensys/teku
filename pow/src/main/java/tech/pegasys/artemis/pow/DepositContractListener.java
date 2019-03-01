package tech.pegasys.artemis.pow;

import com.google.common.eventbus.EventBus;
import io.reactivex.disposables.Disposable;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import tech.pegasys.artemis.pow.api.DepositEvent;
import tech.pegasys.artemis.pow.api.Eth2GenesisEvent;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.pow.contract.DepositContract.DepositEventResponse;
import tech.pegasys.artemis.pow.contract.DepositContract.Eth2GenesisEventResponse;
import tech.pegasys.artemis.pow.event.Deposit;
import tech.pegasys.artemis.pow.event.Eth2Genesis;

import static tech.pegasys.artemis.pow.contract.DepositContract.DEPOSIT_EVENT;
import static tech.pegasys.artemis.pow.contract.DepositContract.ETH2GENESIS_EVENT;

public class DepositContractListener {
  private final EventBus eventBus;

  public Disposable depositEventSub;
  public Disposable eth2GenesisEventSub;

  public DepositContractListener(EventBus eventBus, DepositContract contract) {
    this.eventBus = eventBus;

    // Filter by the contract address and by begin/end blocks
    EthFilter depositEventFilter =
            new EthFilter(
                    DefaultBlockParameterName.EARLIEST,
                    DefaultBlockParameterName.LATEST,
                    contract.getContractAddress().substring(2))
                    .addSingleTopic(EventEncoder.encode(DEPOSIT_EVENT));

    EthFilter eth2GenesisEventFilter =
            new EthFilter(
                    DefaultBlockParameterName.EARLIEST,
                    DefaultBlockParameterName.LATEST,
                    contract.getContractAddress().substring(2))
                    .addSingleTopic(EventEncoder.encode(ETH2GENESIS_EVENT));

    // Subscribe to the event of a validator being registered in the
    // DepositContract
    depositEventSub =
            contract
                    .depositEventFlowable(depositEventFilter)
                    .subscribe(
                            response -> {
                              DepositEvent(response);
                            });

    // Subscribe to the event when 2^14 validators have been registered in the
    // DepositContract
    eth2GenesisEventSub =
            contract
                    .eth2GenesisEventFlowable(eth2GenesisEventFilter)
                    .subscribe(
                            response -> {
                              Eth2GenesisEvent(response);
                            });
  }

  public void DepositEvent(DepositEventResponse response) {
    DepositEvent event = new Deposit(response);
    this.eventBus.post(event);
  }

  public void Eth2GenesisEvent(Eth2GenesisEventResponse response) {
    Eth2GenesisEvent event = new Eth2Genesis(response);
    this.eventBus.post(event);
  }
}
