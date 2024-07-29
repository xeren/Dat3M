RED='\033[0;31m'
NC='\033[0m' # No Color
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && (pwd -W 2> /dev/null || pwd))
export DAT3M_HOME=${SCRIPT_DIR%/*}
echo -e "\n${RED} BUILDING PASS ${NC}"
mvn package