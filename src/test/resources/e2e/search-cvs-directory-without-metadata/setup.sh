# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_cvs_checkout real-checkout myproject
# A directory that is named CVS but that is not version control metadata.
mkdir -p "$HOME/not-a-checkout/CVS"
echo "a source file" > "$HOME/not-a-checkout/CVS/CvsFile.java"
