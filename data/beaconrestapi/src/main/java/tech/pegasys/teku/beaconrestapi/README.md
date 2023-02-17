# Rest Api Notes

## Resources

* [consensus-specs](https://github.com/ethereum/consensus-specs)
* [beacon-APIs](https://github.com/ethereum/beacon-APIs)
* [Prysm API](https://docs.prylabs.network/docs/how-prysm-works/ethereum-public-api)
* [Lighthouse API](https://lighthouse-book.sigmaprime.io/api-bn.html)

## /node/genesis_time

In [consensus-specs](https://github.com/ethereum/consensus-specs) genesis_time should either return
* 200 code and a numeric
* 500 internal error

Any time a beacon node is in a pre-genesis state, it will be a valid condition to have no
genesis time set. For this reason, it was deemed more appropriate to return:
* 200 code and a numeric
* 204 (no content)
