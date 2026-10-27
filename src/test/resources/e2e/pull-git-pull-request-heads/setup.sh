# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
make_git_remote other
clone_git remote clone
git -C clone remote set-url origin https://github.com/example/remote.git
git -C clone config url."$HOME/remote.git".insteadOf https://github.com/example/remote.git
git -C clone remote add other "$HOME/other.git"
# GitHub keeps each pull request's head in a ref like these.
git -C remote.git update-ref refs/pull/1/head master
git -C remote.git update-ref refs/pull/2/head master
git -C other.git update-ref refs/pull/3/head master
