# Rest Api Notes

## Resources

* [Prysm API]("https://api.prylabs.net/#")
* [Lighthouse API]("https://lighthouse-book.sigmaprime.io/http.html")
* [eth2-api]("https://github.com/ethereum/eth2.0-specs")

## /node/genesis_time

In [eth2-api]("https://github.com/ethereum/eth2.0-specs") genesis_time should either return
* 200 code and a numeric
* 500 internal error

Any time a beacon node is in a pre-genesis state, it will be a valid condition to have no
genesis time set. For this reason, it was deemed more appropriate to return:
* 200 code and a numeric
* 204 (no content)

## /beacon/state

In [Lighthouse]("https://lighthouse-book.sigmaprime.io/http_beacon.html#beaconstate") the beacon state can 
be queried by slot or root, and only root search is currently implemented in Teku. eth2-api also suggests
searching by slot.