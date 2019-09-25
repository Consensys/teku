#!/bin/sh

# **** Usage **** #

usage() {
  echo "Runs a simulation of artemis with NODES nodes, where NODES > 0 and NODES < 256"
  echo "Usage: sh run.sh {--numNodes=NODES|-n=NODES} [--networkMode=jvmlibp2p|-m=jvmlibp2p] [--inputFile=INPUT|-i=INPUT]"
  echo "                 [--help|-h]"
  echo "If input files are specifed for specific nodes, those input files will be used to"
  echo "configure their respective nodes."
}

# **** Script **** #

# Get the working directory of this script file
DIR=$(dirname $0)

# Create the arrays to hold the input files that were specified 
INPUTS=()

# Source the functions from the utilities script
source $DIR/run_utils.sh

# Parse the inputs to the script
for arg in "$@"
do 
  shift
  case "$arg" in

    # Match the -n or --numNodes option and set NODES to the provided argument
    -n=*|--numNodes=*)
      if [ "$NODES" != "" ]
      then 
        usage >&2; exit 1
      fi
      NODES="${arg#*=}" ;;

    # Match the -m or --networkMode option and set MODE to the provided argument
    -m=*|--NetworkMode=*)
      if [ "$MODE" != "" ]
      then
        usage >&2; exit 1
      fi
      MODE="${arg#*=}" ;;

    # Match the -i or --inputFile option and update the INPUTS array with the output file path
    "--inputFile"*) 
      FILE="${arg#*=}"
      IDX=$(echo $FILE | sed -E "s/.*[a-zA-Z0-9]+\.([0-9]+)\.toml/\1/")
      # If the input file for a given node is ambiguous, pipe the usage statement to stderr and exit
      # with exit code 2
      if [ "${INPUTS[$IDX]}" != "" ]
      then 
        usage >&2; exit 2
      fi 

      INPUTS[$IDX]="$FILE" ;;

    # Print the usage and exit if the help flag is provided 
    -h|--help) usage; exit 0 ;;

	-v|--vertical)
		VERTICAL=true ;;

    # Pipe the usage to stderr and exit on exitcode 1 if an incorrect flag is provided
    --*) usage >&2; exit 1 ;;
  esac
done

# If NODES is not an integer or is an invalid number, pipe the usage statement to stderr and exit
# with exit code 3
if [ "$(echo "$NODES" | sed "s/[0-9]//g")" != "" ] || [[ $NODES -lt 1 || $NODES -gt 255 ]] 
then 
  usage >&2; exit 3
fi

# If MODE is undefined default to jvmlibp2p
[[ -z "$MODE" ]] && MODE="jvmlibp2p"

# If MODE is not a valid input pipe the usage statement to stderr and exit
# with exit code 3
if [ "$MODE" != "jvmlibp2p" ]
then
  usage >&2; exit 3
fi

# Clean the demo directory
rm -rf ./demo
mkdir -p ./demo

# Clean out the old configuration files
rm -f ../config/runConfig.*
rm -f ../config/*.dat

START_DELAY=20
CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))

# Create the binaries, configuration files, and symlinks for each node
i=0
while [ $i -lt $NODES ] 
do
  configure_node $MODE $i $NODES ${INPUTS[$i]}
  i=$(($i + 1))
done

# Create a properly formatted tmux session for the simulation
create_tmux_windows $NODES $VERTICAL
