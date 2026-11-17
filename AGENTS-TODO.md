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

- [ ] Harness: system property `mvc.test.command` / Gradle property
  `-Pe2e.command=...` runs an external command instead of the Java program.
- [ ] README: language-neutral description of invocation (working directory,
  HOME, `--home`, environment variables, `user.home`), compared files,
  normalization, and how to use `-Pe2e.command`.
- [ ] Usage/option errors: unknown option, non-integer `--timeout`, uppercase
  action, `-q=false` short option, abbreviation `s` for status.
- [ ] Checkouts-file parsing: CRLF line endings, two spaces before module,
  header with extra word / lowercase header, relative directory, relative
  `--checkouts` path, checkouts file that is a directory, empty file.
- [ ] Dry-run status for every VCS type (fake directories) with
  `--git-arg`, `--hg-arg`, `--svn-arg`, `--cvs-arg`, `--insecure`.
- [ ] Mercurial googlecode/codespot certificate workaround (dry run, status
  and pull).
- [ ] Fake-CVS status and pull (replacers) via `--cvs-executable`.
- [ ] Search: hg clone whose default path has a trailing slash (no hg needed).
- [ ] `--print-directory` for a non-git (hg) clone.
- [ ] Clone: dry run of an existing directory; real hg clone; real svn
  checkout; clone timeout is ten times `--timeout`.
- [ ] Pull: real svn update; git with no tracking information; git merge
  conflict.
- [ ] Status: hg file states (added, removed, missing, untracked); hg shelved
  changes; svn added/unversioned files; git merge conflict.
- [ ] Run `./gradlew test -Pregenerate`, inspect every new goal file, write
  `notes`, record bugs.
- [ ] `./gradlew spotlessApply build`; markdownlint; shellcheck of new
  scripts; final commit; delete this file and commit.
