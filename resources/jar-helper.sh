# to run the JarHelper main
# $1 is version of polarize $2 is path to rhsm-qe, $3 is name of rhsm_qe jar, $4 is where to save file to
RHSM_QE=$2
RHSM_QE_JAR=${RHSM_QE}/target/$3
OUTPUT=$4

pushd $RHSM_QE
lein clean
lein uberjar
popd

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5009 \
-cp ./build/libs/polarize-$1-SNAPSHOT-all.jar:${RHSM_QE_JAR} com.github.redhatqe.polarize.JarHelper \
--jar file://${RHSM_QE_JAR} \
--packages "rhsm.cli.tests,rhsm.gui.tests" \
--output $OUTPUT