# Plan: extend the end-to-end tests of mvc

**This temporary plan file must be deleted before any pull request is merged.**

Goal: improve the end-to-end test suite of plume-lib/multi-version-control
("mvc").  Work only in the worktree
`/home/mernst/java/plume-lib/multi-version-control-fork-mernst-branch-e2e-tests`,
on branch `e2e-tests`.  Commit at each milestone (including this file); never
push, create pull requests, change branches, or rewrite history.

The tests are data files under `src/test/resources/e2e/` (one directory per
test case; format in `src/test/resources/e2e/README.md`), run by
`src/test/java/org/plumelib/multiversioncontrol/EndToEndTest.java`.  The data
format must be usable by a harness for a different implementation in a different
language.  Do not change the main source code.  For a bug, add a `notes` file
that says the current output is wrong, and record current behavior in the goal
files.  Tests must not use the network; tests that need hg/svn list them in
`requires`.  `./gradlew build` must pass at the end; Markdown must pass
markdownlint-cli2.  When every entry below is done, delete this file and commit
the deletion.

There are no other forms of end-to-end tests (no shell-script tests, no Java
tests other than the harness), so nothing needs converting.

## Checklist

Done so far:  harness option `-Pe2e.command`, README rewrite, and 35 new test
cases with goal files (all committed).

- [ ] `./gradlew build` passes; final commit; delete this file and commit.
