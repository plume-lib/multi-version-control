# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_git_remote remote
clone_git remote clone
# A local branch whose commit is on no remote branch, so that the program would fetch the
# pull-request heads of the GitHub remote.
echo "unpushed" > clone/file.txt
commit_git clone "Unpushed"
# Make the remote repository unreachable, through an ssh command that responds as GitHub does to a
# request for a repository that does not exist.
cat > "$HOME/fake-ssh" <<'END'
#!/bin/sh
echo "ERROR: Repository not found." >&2
exit 1
END
chmod +x "$HOME/fake-ssh"
git -C "$HOME/clone" remote set-url origin git@github.com:nonexistent/repo.git
git -C "$HOME/clone" config core.sshCommand "$HOME/fake-ssh"
