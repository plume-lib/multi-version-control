# End-to-end tests

Each subdirectory of this directory is one end-to-end test case.  The test
harness, `src/test/java/org/plumelib/multiversioncontrol/EndToEndTest.java`,
runs the program in a subprocess and compares its output against the goal files
in the test case directory.  No test inspects the program's internal state, and
no test needs network access.

Each test runs in a fresh temporary directory that is used as both the working
directory and `$HOME`, so a test cannot see, and cannot damage, the real home
directory.  The subprocess ignores the user's git, Mercurial, and Subversion
configuration and uses a fixed locale, time zone, and commit identity, so that
the output does not depend on who runs the tests.

## Files in a test case directory

All of these are optional except `args`.

- `args`: The command-line arguments, one per line.  A blank line, or a line
  whose first non-whitespace character is `#`, is ignored.  A line consisting of
  `""` denotes an empty argument.  `${HOME}` is replaced by the temporary home
  directory.  The program is always run with `--home` set to that directory,
  before these arguments.
- `mvc-checkouts`: Copied to `$HOME/.mvc-checkouts`, with `${HOME}` replaced as
  for `args`.
- `setup.sh`: A bash script that creates the repositories that the test needs.
  It runs before the program, in the temporary home directory.
- `expected-out`: Goal file for standard output.  If absent, standard output
  must be empty.
- `expected-err`: Goal file for standard error.  If absent, standard error must
  be empty.
- `expected-status`: The expected exit status.  If absent, the exit status must
  be 0.
- `postcheck.sh`: A bash script that prints information about the files that the
  program wrote.  It runs after the program, in the temporary home directory.
  This is how a test checks program output that is a file rather than text on
  standard out.
- `expected-postcheck`: Goal file for the output of `postcheck.sh`.
- `requires`: Whitespace-separated names of programs that must be on the PATH.
  If any is missing, the test is skipped rather than failed.
- `sort-output`: If this file exists, the lines of standard output are sorted
  before comparison.  Use it only where the program does not define the output
  order.
- `notes`: Commentary for human readers; the harness ignores it.

Absolute pathnames vary from run to run, so before comparing the program's
output to a goal file, the harness replaces the temporary home directory by
`${HOME}` and the real home directory of the user who is running the tests by
`${USER_HOME}`.

`common.sh` is not a test case; it holds shell functions that the `setup.sh`
scripts share.

## Adding a test case

Create a directory with an `args` file and whatever inputs the test needs, then
run:

```sh
./gradlew test -Pregenerate
```

That writes the goal files from the program's actual output, for all test cases.
Inspect the diffs before committing them: a goal file should record what the
program ought to print, not merely what it does print.  Where the program's
current output is wrong, the test case has a `notes` file that says so.
