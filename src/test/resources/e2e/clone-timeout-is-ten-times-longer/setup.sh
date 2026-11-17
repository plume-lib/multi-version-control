# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
# A fake git that takes 2 seconds, which is longer than --timeout but shorter than 10 times it,
# and then creates the clone's directory.
printf '#!/bin/sh\nsleep 2\nmkdir -p options/.git\n' > "$HOME/slow-git"
chmod +x "$HOME/slow-git"
