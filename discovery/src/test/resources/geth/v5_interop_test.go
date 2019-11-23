package discover

import (
	"crypto/ecdsa"
	"fmt"
	"github.com/ethereum/go-ethereum/crypto"
	"github.com/ethereum/go-ethereum/internal/testlog"
	"github.com/ethereum/go-ethereum/log"
	"github.com/ethereum/go-ethereum/p2p/enode"
	"github.com/ethereum/go-ethereum/p2p/enr"
	"net"
	"os"
	"strconv"
	"testing"
	"time"
)

// Start two servers on 30302 and 30303 ports, run for $TEST_DURATION seconds, exit
func TestNodesServer(t *testing.T) {
	var nodes []*UDPv5
	startPort := 30302
	for i := 0; i < 2; i++ {
		var cfg Config
		if len(nodes) > 0 {
			bn := nodes[0].Self()
			cfg.Bootnodes = []*enode.Node{bn}
		}
		var key *ecdsa.PrivateKey
		if i == 1 {
			// Predefined key for server on 30303 port, so we could easily connect to it outside the test
			key, _ = crypto.HexToECDSA("fb757dc581730490a1d7a00deea65e9b1936924caaea8f44d476014856b68736")
		}
		curnode := startLocalhostOnPortV5(t, cfg, startPort+i, key)
		fmt.Printf("port %v: %v\n", startPort+i, curnode.localNode.Node())
		fillTable(curnode.tab, Map(nodes, func(pv5 *UDPv5) *node {
			return wrapNode(pv5.localNode.Node())
		})) // force fill buckets
		<-curnode.tab.initDone
		nodes = append(nodes, curnode)
		defer curnode.Close()
	}

	// Wait for interop call
	fmt.Printf("Waiting for external nodes query\n")
	c1 := make(chan string, 1)

	// Run your long running function in it's own goroutine and pass back it's
	// response into our channel.
	go func() {
		text := LongRunningProcess()
		c1 <- text
	}()

	// Listen on our channel AND a timeout channel - which ever happens first.
	duration, err := strconv.Atoi(os.Getenv("TEST_DURATION"))
	if err != nil {
		panic("TEST_DURATION env not set or failed to be parsed" + err.Error())
	}
	select {
	case res := <-c1:
		fmt.Println(res)
	case <-time.After(time.Duration(duration) * time.Second):
		fmt.Println("out of time :(")
	}
}

func LongRunningProcess() string {
	time.Sleep(9000 * time.Hour)
	return "You will never see this :)"
}

func Map(vs []*UDPv5, f func(*UDPv5) *node) []*node {
	vsm := make([]*node, len(vs))
	for i, v := range vs {
		vsm[i] = f(v)
	}
	return vsm
}

func startLocalhostOnPortV5(t *testing.T, cfg Config, port int, key *ecdsa.PrivateKey) *UDPv5 {
	if key == nil {
		cfg.PrivateKey = newkey()
	} else {
		cfg.PrivateKey = key
	}
	db, _ := enode.OpenDB("")
	ln := enode.NewLocalNode(db, cfg.PrivateKey)

	// Prefix logs with node ID.
	lprefix := fmt.Sprintf("(%s)", ln.ID().TerminalString())
	lfmt := log.TerminalFormat(false)
	cfg.Log = testlog.Logger(t, log.LvlTrace)
	cfg.Log.SetHandler(log.FuncHandler(func(r *log.Record) error {
		t.Logf("%s %s", lprefix, lfmt.Format(r))
		return nil
	}))

	// Listen.
	socket, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IP{127, 0, 0, 1}, Port: port})
	if err != nil {
		t.Fatal(err)
	}
	realaddr := socket.LocalAddr().(*net.UDPAddr)
	ln.SetStaticIP(realaddr.IP)
	ln.Set(enr.UDP(realaddr.Port))
	udp, err := ListenV5(socket, ln, cfg)
	if err != nil {
		t.Fatal(err)
	}
	return udp
}
