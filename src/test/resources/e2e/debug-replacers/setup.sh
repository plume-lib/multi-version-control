# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
mkdir -p "$HOME/wc/.svn"
# A fake svn program, so that this test does not depend on svn being installed and so that its
# output is the same everywhere.  The first line is long because the last part of the debugging
# output dumps the first 100 characters of the result; keeping the absolute directory beyond
# character 100 keeps this test's goal file independent of where the test runs.
{
  echo '#!/bin/sh'
  echo 'echo "svn: W200000: a warning that no replacer matches, long enough that the absolute directory below starts after character 100"'
  echo 'echo "M       file.txt"'
} > "$HOME/fake-svn"
chmod +x "$HOME/fake-svn"
