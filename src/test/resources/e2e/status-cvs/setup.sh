# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_cvs_checkout mod mod
# A fake cvs program, so that this test does not depend on cvs being installed.  It prints what
# "cvs -q diff -b --brief -N" prints for a modified file, a new file, an unknown file, and a
# subdirectory, and exits with status 1, as cvs does when files differ.
cat > "$HOME/fake-cvs" <<'END'
#!/bin/sh
cat <<'OUTPUT'
Index: changed.txt
===================================================================
RCS file: /cvsroot/mod/changed.txt,v
retrieving revision 1.1
diff -b --brief -N -r1.1 changed.txt
Files /tmp/cvsa1 and changed.txt differ
Index: added.txt
===================================================================
RCS file: added.txt
diff -N added.txt
? unknown.txt
cvs diff: in directory sub:
cvs diff: cannot find gone.txt
OUTPUT
exit 1
END
chmod +x "$HOME/fake-cvs"
