Docker Testnet
==============

This directory contains a docker-compose configuration to run a basic 4-node Teku network.
The network uses the minimal spec and the mock-genesis protocol to generate a genesis state without 
needing an ETH1 chain.  The 64 validators are spread across the four nodes.


How To Run
----------

To start the network, run the `launch.sh` script in this directory.  
To stop, simply ctrl-C to kill the docker instances and then optionally run `docker-compose down` to remove the stopped instances from docker.


Ports and Access
----------------

Metrics are available via Grafana at http://localhost:3001/ username is `admin` and password is `pass`.

Each node's REST APIs are exposed on ports 19601, 19602, 19603 and 19604 respectively.

Add a `ports:` section to any of the nodes to expose additional ports and access services from that node.

Persistent Data
---------------

The teku nodes store data and logs in `data/node<X>/`. It is safe to delete the entire `data` directory to start from scratch.

Grafana stores it's data in `grafana/data` so changes made to dashboards are persisted across runs.
