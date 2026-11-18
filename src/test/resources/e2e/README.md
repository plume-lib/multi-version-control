# End-to-end tests

Each subdirectory of this directory is one end-to-end test case.  A test case
is data, not code:  it gives the program's inputs and the program's expected
outputs.  So, the test cases can be run against any implementation of `mvc`,
in any language, by a harness that follows this document.

The harness for this implementation is
`src/test/java/org/plumelib/multiversioncontrol/EndToEndTest.java`.  It runs
the program in a subprocess and compares the program's output against the goal
files in the test case directory.  No test inspects the program's internal
state, and no test needs network access.

## Files in a test case directory

All of these are optional except `args`.

- `args`:  The command-line arguments, one per line.  Leading and trailing
  whitespace of each line is removed.  A blank line, or a line whose first
  non-whitespace character is `#`, is ignored.  A line consisting of `""`
  denotes an empty argument.  Each `${HOME}` is replaced by the temporary home
  directory.
- `mvc-checkouts`:  Copied to `$HOME/.mvc-checkouts`, with `${HOME}` replaced as
  for `args`.  Its line terminators are copied unchanged (one test case uses
  CRLF).
- `setup.sh`:  A bash script that creates the files and repositories that the
  test needs, such as clones and fake version control programs.  It runs before
  the program.  If it exits with a non-zero status, the test fails.
- `expected-out`:  Goal file for standard output.  If absent, standard output
  must be empty.
- `expected-err`:  Goal file for standard error.  If absent, standard error must
  be empty.
- `expected-out-prefix`:  Goal file for the beginning of standard output.
  Standard output must start with the contents of this file, and anything can
  follow.  An empty file therefore permits any standard output.  Use this
  instead of `expected-out` where the rest of the output depends on the
  implementation, such as the list of options in a usage message.  It is an
  error for both `expected-out` and `expected-out-prefix` to exist.
- `expected-err-prefix`:  Like `expected-out-prefix`, but for standard error.
- `expected-out-suffix`, `expected-err-suffix`:  Goal files for the end of
  standard output or standard error.  The stream must end with the contents of
  this file.
- `expected-out-contains`, `expected-err-contains`:  Strings, one per line,
  each of which must appear somewhere in standard output or standard error.
  Blank lines are ignored.
- A test case can have any combination of the prefix, suffix, and contains
  files for a stream, which are called partial goal files, but none of them
  together with the stream's full goal file (`expected-out` or
  `expected-err`).
- `expected-status`:  The expected exit status, as a decimal number followed by
  a line terminator.  If absent, the exit status must be 0.
- `postcheck.sh`:  A bash script that prints information about the files that
  the program wrote.  It runs after the program.  This is how a test checks
  program output that is a file rather than text on standard out.  If it exits
  with a non-zero status, the test fails.
- `expected-postcheck`:  Goal file for the standard output of `postcheck.sh`.
  If absent, that output must be empty.  It is an error for this file to exist
  without `postcheck.sh`.
- `requires`:  Whitespace-separated names of programs that must be on the PATH,
  such as `git`, `hg`, `svn`, and `svnadmin`.  If any is missing, the test is
  skipped rather than failed.  A failure message states these programs'
  versions (the first line of the output of `PROGRAM --version`), because a
  goal file might record the exact wording of their messages.
- `sort-output`:  If this file exists, the lines of standard output are sorted
  lexicographically before comparison.  Use it only where the program does not
  define the output order.
- `notes`:  Commentary for human readers; the harness ignores it.

A test case directory must not contain any other file.  Without that
restriction, a misspelled file name would silently weaken or disable part of the
test:  a misspelled goal file would change the test into "this stream must be
empty", and a misspelled `sort-output` would turn off sorting.

`common.sh` is not a test case; it holds shell functions that the `setup.sh`
and `postcheck.sh` scripts share.  A script loads it with
`. "$(dirname "$0")/../common.sh"`, so a harness must run each script by its
path in the test case directory.

## How a harness runs one test case

1. If a program named in `requires` is not on the PATH, skip the test case.
2. Create a fresh, empty temporary directory, and use its canonical (symbolic
   link free) absolute path as the home directory, `$HOME`.  A test therefore
   cannot see, and cannot damage, the real home directory.
3. Write `$HOME/.mvc-checkouts` from `mvc-checkouts`, if it exists.
4. Run `bash setup.sh`, if it exists.
5. Run the program, with arguments `--home=$HOME` followed by the arguments
   from `args`.
6. Compare the program's standard output, standard error, and exit status to
   the goal files, after normalizing them as described below.  For a stream
   with partial goal files, check only what those files specify:  the
   beginning of the stream, its end, and strings that it contains.
7. Run `bash postcheck.sh`, if it exists, and compare its normalized standard
   output to `expected-postcheck`.

Every subprocess (`setup.sh`, the program, and `postcheck.sh`) runs with
`$HOME` as its working directory, with nothing written to its standard input,
and with this environment:

- The harness's own environment, minus every variable whose name starts with
  `GIT_`, `HG`, or `SVN_`, and minus `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, and
  `JDK_JAVA_OPTIONS`.  (The last three make a JVM print a "Picked up ..."
  line.)
- These settings, which make the version control programs ignore the user's
  configuration and make the output independent of who runs the tests, and
  when:

  ```sh
  HOME=<the temporary home directory>
  XDG_CONFIG_HOME=$HOME/.config
  GIT_CONFIG_GLOBAL=/dev/null
  GIT_CONFIG_SYSTEM=/dev/null
  GIT_AUTHOR_NAME="Test User"
  GIT_AUTHOR_EMAIL=test@example.com
  GIT_AUTHOR_DATE=2020-01-01T00:00:00+00:00
  GIT_COMMITTER_NAME="Test User"
  GIT_COMMITTER_EMAIL=test@example.com
  GIT_COMMITTER_DATE=2020-01-01T00:00:00+00:00
  HGRCPATH=/dev/null
  HGUSER="Test User <test@example.com>"
  HGPLAIN=1
  LC_ALL=C
  LANG=C
  TZ=UTC
  ```

The Java implementation also runs with the Java system property `user.home`
set to the fixed placeholder `/nonexistent/user-home`.  The program's usage
message shows that value as the default for `--home`.

## Normalization

Absolute pathnames vary from run to run, so before comparing the program's
output (and the output of `postcheck.sh`) to a goal file, the harness:

- replaces each CRLF line terminator by LF;
- replaces each occurrence of the temporary home directory by `${HOME}`;
- replaces each occurrence of the placeholder `/nonexistent/user-home` by
  `${USER_HOME}`.

From standard error only, the harness also removes the warnings that the JVM
itself prints on some JDK versions:  each maximal run of consecutive lines that
start with `WARNING:`, together with one blank line that follows the run, if
there is one.  Standard output is not filtered this way, because a line of the
program's own standard output might start with `WARNING:`.

No other normalization is done.  In particular, the comparison is exact,
including whitespace and the final line terminator.

## Running the tests

To run the tests against this implementation:

```sh
./gradlew test
```

To run the tests against a different implementation, pass the command that
runs it:

```sh
./gradlew test -Pe2e.command="python3 /path/to/mvc.py"
```

The value is split into words at whitespace, without shell quoting, and the
harness appends `--home=$HOME` and the arguments from `args`.  (This sets the
Java system property `mvc.test.command`, which the harness reads.)

No goal file depends on the Java implementation's command-line parsing
library or on the wording of a Java exception.  Where the program's output
contains such text, as in a usage message or after the program's own "Problem
reading file NAME:" message, the test case has a prefix goal file
(`expected-out-prefix` or `expected-err-prefix`) that checks only the part of
the output that the program itself determines.  The one exception is a test
case that records a bug whose current behavior is a Java stack trace; any
correct implementation fails that test case, as it fails every test case that
records a bug.

The format of the debugging output that `--debug`, `--debug-replacers`, and
`--debug-process-output` print is up to the implementation, so the `debug-*`
test cases use suffix and contains goal files to check the output of the
action and a few strings that the debugging output must mention.

## Adding a test case

Create a directory with an `args` file and whatever inputs the test needs, then
run:

```sh
./gradlew test -Pregenerate
```

That writes the goal files from the program's actual output, for all test cases.
It does not write a partial goal file (such as `expected-out-prefix`), because
a person chooses how much of the output a partial goal file checks;
regeneration leaves each partial goal file unchanged and still compares the
output against it.
Inspect the diffs before committing them: a goal file should record what the
program ought to print, not merely what it does print.  Where the program's
current output is wrong, the test case has a `notes` file that says so.

Regeneration skips a test case whose `requires` programs are not installed, and
leaves that test case's goal files unchanged; it prints the name of each such
test case.  So, on a machine without Mercurial or Subversion, the absence of a
diff for an hg or svn test case does not mean that its goal files are up to
date.
