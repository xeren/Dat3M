SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && (pwd -W 2> /dev/null || pwd))
export DAT3M_HOME=${SCRIPT_DIR%/*}
export CFLAGS="-I$DAT3M_HOME/include"
export OPTFLAGS="-mem2reg -sroa -early-cse -indvars -loop-unroll -fix-irreducible -loop-simplify -simplifycfg -gvn"
MAVEN_OPTS='-Djava.library.path=target/libs' mvn exec:java