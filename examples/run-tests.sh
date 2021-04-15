set -e

echo "Running tests..."

VMNAME=""
VM=""

TEST_ROOT=$(pwd)
#TEST_JDKS_DIR="${TEST_ROOT}/../test-jdks"

OTHER_OPTS=" -Xmx128M -Xms128M"
JFR_OPTS="-XX:StartFlightRecording=duration=10360s,filename=XXXX.jfr"
VITALS_OPTS="-XX:+UnlockDiagnosticVMOptions -XX:+DumpVitalsAtExit -XX:VitalsSampleInterval=1 -XX:VitalsFile=XXXX"

# $1 VM name
# $2 VM options
# $3 test name
# $4 test class and options
run_test() {

	local VMNAME=${1}
  local JHOME=${TEST_JDKS_DIR}/${VMNAME}

	local UNIQUE_NAME="${VMNAME}-${3}"

	local VMOPTS="$2"
	local VMOPTS=${VMOPTS//XXXX/${UNIQUE_NAME}}

	local LOGFILE="out-${UNIQUE_NAME}.log"

 	${JHOME}/bin/java -version >$LOGFILE 2>&1

	COMMAND="${JHOME}/bin/java ${VMOPTS} -cp ${TEST_ROOT}/target/examples-1.0.jar ${4}"
	echo "Running: ${COMMAND}"
	${COMMAND} >>$LOGFILE 2>&1 &

}

# $1 TEST NAME
# $2 test class and options
run_one_test_on_all_vms() {

	echo "Running $1..."

	mkdir -p $1
	pushd $1

	local ALL_VM_OPTS="$OTHER_OPTS $JFR_OPTS $VITALS_OPTS"

	run_test sapmachine15 "$ALL_VM_OPTS" "$1" "$2"

	run_test sapmachine16 "$ALL_VM_OPTS" "$1" "$2"

#	run_test sapmachine16 "$ALL_VM_OPTS -XX:MetaspaceReclaimPolicy=aggressive" "${1}-aggr" "$2"

    wait

	popd 

}

run_all_tests() {

	local SDATE=$(date -I)
	local I=0
        set +e
	while [ -d "./results/${SDATE}-${I}" ]; do
		let "I++"
	done
	set -e

	local RESULTS_DIR="./results/${SDATE}-${I}"
	mkdir -p $RESULTS_DIR

	pushd $RESULTS_DIR

	echo "Directory is $RESULTS_DIR"

	# Elasticity
	run_one_test_on_all_vms "elast-lofrag" "de.stuefe.repros.metaspace.HighFragTest --interleave=0.1"
	run_one_test_on_all_vms "elast-hifrag" "de.stuefe.repros.metaspace.HighFragTest --interleave=0.01"

	# Effect of small loaders
	run_one_test_on_all_vms "many-loaders-250-8" "de.stuefe.repros.metaspace.SmallLoadersTest --num-loaders=250 --num-classes=8"
	run_one_test_on_all_vms "many-loaders-1000-2" "de.stuefe.repros.metaspace.SmallLoadersTest --num-loaders=1000 --num-classes=2"

	popd

	echo "Done."
}

run_all_tests

