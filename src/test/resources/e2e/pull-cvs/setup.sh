# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_cvs_checkout mod mod
# A fake cvs program, so that this test does not depend on cvs being installed.  It prints messages
# that "cvs -Q update -d" prints, and exits with status 1, as cvs does when an update fails.
cat > "$HOME/fake-cvs" <<'END'
#!/bin/sh
cat <<'OUTPUT'
cvs update: move away in-the-way.txt; it is in the way
C in-the-way.txt
cvs update: skipping directory sub
cvs update: conflicts found in conflicted.txt
cvs [update aborted]: cannot open directory /cvsroot/mod/gone: No such file or directory
OUTPUT
exit 1
END
chmod +x "$HOME/fake-cvs"
