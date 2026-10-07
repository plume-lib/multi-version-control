# shellcheck shell=bash
# Shared helpers for the setup.sh and postcheck.sh scripts of the end-to-end tests.
# A script uses these by starting with:  . "$(dirname "$0")/../common.sh"
# Each script runs with the test's temporary home directory as both $HOME and the working
# directory, and with the user's git/Mercurial/Subversion configuration disabled.

set -e

# make_git_remote NAME:  create bare repository $HOME/NAME.git containing one commit of file.txt.
make_git_remote() {
  git init -q -b master "$1-seed"
  echo "first line" > "$1-seed/file.txt"
  git -C "$1-seed" add file.txt
  git -C "$1-seed" commit -q -m "Initial commit"
  git clone -q --bare "$1-seed" "$1.git"
  rm -rf "$1-seed"
}

# clone_git REMOTE DIR:  clone $HOME/REMOTE.git into $HOME/DIR.
clone_git() {
  git clone -q "$HOME/$1.git" "$2"
}

# commit_git DIR MESSAGE:  commit all changes in $HOME/DIR.
commit_git() {
  git -C "$1" add -A
  git -C "$1" commit -q -m "$2"
}

# make_hg_repo DIR:  create Mercurial repository $HOME/DIR containing one commit of file.txt.
make_hg_repo() {
  hg init "$1"
  echo "first line" > "$1/file.txt"
  hg -R "$1" add -q "$1/file.txt"
  hg -R "$1" commit -q -m "Initial commit"
}

# make_cvs_checkout DIR MODULE:  create a directory that looks like a CVS checkout.  Creating a
# real one would require the cvs program, which these tests do not depend on; the program reads
# only the two files that this function writes.
make_cvs_checkout() {
  mkdir -p "$1/CVS"
  echo ":pserver:anonymous@cvs.example.com:/cvsroot" > "$1/CVS/Root"
  echo "$2" > "$1/CVS/Repository"
}

# make_svn_checkout REPO DIR:  create Subversion repository $HOME/REPO and check it out into
# $HOME/DIR, with one commit of file.txt.
make_svn_checkout() {
  svnadmin create "$1"
  svn -q checkout "file://$HOME/$1" "$2"
  echo "first line" > "$2/file.txt"
  svn -q add "$2/file.txt"
  svn -q commit -m "Initial commit" "$2"
}
