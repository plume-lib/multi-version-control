package org.plumelib.multiversioncontrol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * End-to-end tests for {@link MultiVersionControl}. Each test runs the program in a subprocess and
 * compares the program's output against goal files. No test inspects the program's internal state,
 * and no test needs network access.
 *
 * <p>Each test case is a directory under {@code src/test/resources/e2e}. The directory contains
 * these files, all of which are optional except {@code args}:
 *
 * <dl>
 *   <dt>{@code args}
 *   <dd>The command-line arguments, one per line. A blank line, or a line whose first
 *       non-whitespace character is {@code #}, is ignored. A line consisting of {@code ""} denotes
 *       an empty argument. In every line, {@code ${HOME}} is replaced by the test's temporary home
 *       directory. The program is always run with {@code --home} set to that directory, before
 *       these arguments.
 *   <dt>{@code mvc-checkouts}
 *   <dd>Copied to {@code $HOME/.mvc-checkouts}, with {@code ${HOME}} replaced as for {@code args}.
 *   <dt>{@code setup.sh}
 *   <dd>A bash script that creates the repositories that the test needs. It is run before the
 *       program, with the temporary home directory as its working directory and as {@code $HOME}.
 *   <dt>{@code expected-out}, {@code expected-err}
 *   <dd>Goal files for the program's standard output and standard error. A missing goal file means
 *       that the stream is expected to be empty.
 *   <dt>{@code expected-status}
 *   <dd>The expected exit status; the default is 0.
 *   <dt>{@code postcheck.sh}, {@code expected-postcheck}
 *   <dd>A bash script that prints information about the files that the program wrote (it is run
 *       after the program, in the temporary home directory), and the goal file for its output. This
 *       is how a test checks a program output that is a file rather than text on standard out.
 *   <dt>{@code requires}
 *   <dd>Whitespace-separated names of programs that must be on the PATH. If any is missing, the
 *       test is skipped rather than failed. A failure message states these programs' versions,
 *       because a goal file may record the exact wording of their messages.
 *   <dt>{@code sort-output}
 *   <dd>If this (possibly empty) file exists, the lines of standard output are sorted before
 *       comparison. Use this only for a test whose output order the program does not define.
 *   <dt>{@code notes}
 *   <dd>Commentary for human readers; the test harness ignores it.
 * </dl>
 *
 * <p>Absolute pathnames vary from run to run, so before comparing the program's output to a goal
 * file, the temporary home directory is replaced by {@code ${HOME}} and the home directory of the
 * user who is running the tests is replaced by {@code ${USER_HOME}}. The warnings that the JVM
 * itself prints are removed from standard error, but not from standard output, where a line that
 * starts with "WARNING: " might be the program's own output.
 *
 * <p>A test case directory may not contain any file other than those listed above. Without that
 * restriction, a misspelled file name would silently weaken or disable part of the test.
 *
 * <p>To overwrite the goal files with the program's current output, run {@code ./gradlew test
 * -Pregenerate}. Always inspect the resulting diffs: a goal file should record what the program
 * ought to print, not merely what it does print. Regeneration leaves the goal files of a skipped
 * test case unchanged, so it cannot bring those files up to date on a machine that lacks the
 * programs they require; it prints the name of each such test case.
 */
final class EndToEndTest {

  /** Creates a new EndToEndTest. */
  EndToEndTest() {}

  /** The directory that contains the test case directories. */
  private static final Path casesDir =
      Path.of(propertyOrDefault("mvc.test.casesDir", "src/test/resources/e2e"));

  /** The class path with which to run the program. */
  private static final String classpath =
      propertyOrDefault("mvc.test.classpath", System.getProperty("java.class.path"));

  /** If true, overwrite the goal files instead of just comparing against them. */
  private static final boolean regenerate = Boolean.getBoolean("mvc.test.regenerate");

  /** The {@code java} executable that runs the program. */
  private static final String javaExecutable =
      Path.of(System.getProperty("java.home"), "bin", "java").toString();

  /**
   * The JVM argument that runs the code coverage agent, or the empty string if coverage is not
   * being measured. The program runs in a subprocess, so the agent must run there.
   */
  private static final String jacocoArg = propertyOrDefault("mvc.test.jacocoArg", "");

  /**
   * How many failing tests' temporary directories to leave in place, for a person to examine. The
   * files of any further failure are deleted, so that a run in which many tests fail does not fill
   * up the temporary directory.
   */
  private static final int maxKeptDirectories = 5;

  /** How many failing tests' temporary directories have been left in place. */
  private static int keptDirectories = 0;

  /**
   * How long to wait for a subprocess, in seconds. Every subprocess that a test runs finishes in
   * well under a second, so a longer wait means that something has hung. Fail in that case, rather
   * than blocking the build forever.
   */
  private static final int subprocessTimeoutSeconds = 600;

  /** How long to wait, in seconds, for a subprocess that has been killed to die. */
  private static final int destroyTimeoutSeconds = 10;

  /**
   * The names of the files that may appear in a test case directory. Any other file is an error,
   * because a misspelled name would silently weaken or disable part of the test.
   */
  private static final Set<String> caseFileNames =
      Set.of(
          "args",
          "expected-err",
          "expected-out",
          "expected-postcheck",
          "expected-status",
          "mvc-checkouts",
          "notes",
          "postcheck.sh",
          "requires",
          "setup.sh",
          "sort-output");

  /**
   * Matches the warnings that the JVM prints, on some JDK versions, when SVNKit loads its native
   * library. Whether they appear depends on the JDK version, not on the program, so remove them
   * from standard error. Standard output is not filtered, because a line of the program's own
   * output might start with "WARNING: ".
   */
  private static final Pattern jvmWarnings =
      Pattern.compile("^WARNING: [^\n]*\n(?:WARNING: [^\n]*\n)*\n?", Pattern.MULTILINE);

  /**
   * Returns the value of a system property, or the given default if the property is not set.
   *
   * @param property the name of a system property
   * @param defaultValue the value to use if the property is not set
   * @return the value of the property, or {@code defaultValue}
   */
  private static String propertyOrDefault(String property, String defaultValue) {
    String value = System.getProperty(property);
    return value == null ? defaultValue : value;
  }

  /**
   * Returns one test per test case directory.
   *
   * @return one test per test case directory
   * @throws IOException if the test case directories cannot be read
   */
  @TestFactory
  List<DynamicTest> endToEndTests() throws IOException {
    List<Path> caseDirs;
    try (Stream<Path> entries = Files.list(casesDir)) {
      caseDirs = entries.filter(Files::isDirectory).sorted().toList();
    }
    if (caseDirs.isEmpty()) {
      throw new AssertionError("No test cases in " + casesDir.toAbsolutePath());
    }
    List<DynamicTest> result = new ArrayList<>(caseDirs.size());
    for (Path caseDir : caseDirs) {
      result.add(
          DynamicTest.dynamicTest(caseName(caseDir), caseDir.toUri(), () -> runTestCase(caseDir)));
    }
    return result;
  }

  // //////////////////////////////////////////////////////////////////////
  // Running one test case
  //

  /**
   * Throws an exception if a test case directory contains a file that the test harness does not
   * know about, or a goal file that nothing would ever be compared against. Without this check, a
   * misspelled file name would silently weaken or disable part of the test.
   *
   * @param caseDir the test case directory
   * @throws IOException if the directory cannot be read
   */
  private static void checkCaseFiles(Path caseDir) throws IOException {
    List<String> unrecognized = new ArrayList<>();
    try (Stream<Path> entries = Files.list(caseDir)) {
      for (Path entry : entries.sorted().toList()) {
        String name = String.valueOf(entry.getFileName());
        if (!caseFileNames.contains(name)) {
          unrecognized.add(name);
        }
      }
    }
    if (!unrecognized.isEmpty()) {
      throw new AssertionError(
          "Unrecognized file(s) in " + caseDir + ": " + String.join(" ", unrecognized));
    }
    if (Files.exists(caseDir.resolve("expected-postcheck"))
        && !Files.exists(caseDir.resolve("postcheck.sh"))) {
      throw new AssertionError(
          "Goal file `expected-postcheck`, but no `postcheck.sh`, in " + caseDir);
    }
  }

  /**
   * Runs one test case: run the program, then compare its output to the goal files.
   *
   * @param caseDir the test case directory
   * @throws IOException if a file cannot be read or written
   * @throws InterruptedException if a subprocess is interrupted
   */
  private void runTestCase(Path caseDir) throws IOException, InterruptedException {
    String caseName = caseName(caseDir);
    checkCaseFiles(caseDir);
    List<String> required = whitespaceSeparated(readFileOrEmpty(caseDir.resolve("requires")));
    for (String program : required) {
      if (!onPath(program)) {
        if (regenerate) {
          // A skipped test case's goal files are left as they are.  Say so, because otherwise a
          // developer might conclude from the absence of a diff that they are up to date.
          System.out.println(
              "Not regenerating the goal files of " + caseName + ": no " + program + " on PATH");
        }
        Assumptions.abort("Skipping " + caseName + ": no " + program + " on PATH");
      }
    }

    Path scratch = Files.createTempDirectory("mvc-e2e-").toRealPath();
    // The tests run sequentially, so this is accurate for the whole of this test case.
    boolean keepIfFailed = keptDirectories < maxKeptDirectories;
    boolean passed = false;
    try {
      Path home = Files.createDirectory(scratch.resolve("home"));
      String versions = versionsOf(required, home, scratch);

      Path checkoutsFile = caseDir.resolve("mvc-checkouts");
      if (Files.exists(checkoutsFile)) {
        Files.writeString(
            home.resolve(".mvc-checkouts"), substituteHome(readFile(checkoutsFile), home), UTF_8);
      }

      Path setupScript = caseDir.resolve("setup.sh");
      if (Files.exists(setupScript)) {
        Result setup = run(List.of("bash", setupScript.toAbsolutePath().toString()), home, scratch);
        if (setup.status() != 0) {
          throw new AssertionError(
              "setup.sh failed for "
                  + caseName
                  + "\n"
                  + whereFiles(home, keepIfFailed)
                  + versions
                  + setup.describe());
        }
      }

      List<String> command = new ArrayList<>();
      command.add(javaExecutable);
      if (!jacocoArg.equals("")) {
        command.add(jacocoArg);
      }
      command.add("-ea");
      command.add("-cp");
      command.add(classpath);
      command.add(MultiVersionControl.class.getName());
      command.add("--home=" + home);
      for (String arg : arguments(caseDir)) {
        command.add(substituteHome(arg, home));
      }
      Result result = run(command, home, scratch);
      String context = context("The program", home, keepIfFailed, versions, result);

      String stdout = normalize(result.stdout(), home);
      if (Files.exists(caseDir.resolve("sort-output"))) {
        stdout = sortLines(stdout);
      }
      checkGoal(caseDir.resolve("expected-out"), stdout, "standard output", context);
      checkGoal(
          caseDir.resolve("expected-err"),
          normalizeStderr(result.stderr(), home),
          "standard error",
          context);
      checkGoal(caseDir.resolve("expected-status"), result.status() + "\n", "exit status", context);

      Path postcheckScript = caseDir.resolve("postcheck.sh");
      if (Files.exists(postcheckScript)) {
        Result postcheck =
            run(List.of("bash", postcheckScript.toAbsolutePath().toString()), home, scratch);
        if (postcheck.status() != 0) {
          throw new AssertionError(
              "postcheck.sh failed for "
                  + caseName
                  + "\n"
                  + whereFiles(home, keepIfFailed)
                  + versions
                  + postcheck.describe());
        }
        checkGoal(
            caseDir.resolve("expected-postcheck"),
            normalize(postcheck.stdout(), home),
            "postcheck output",
            context("postcheck.sh", home, keepIfFailed, versions, postcheck));
      }
      passed = true;
    } finally {
      // On failure, leave the files in place for a person to examine; the failure message says
      // where they are.  Do that for at most `maxKeptDirectories` failures, so that a run in which
      // many tests fail does not fill up the temporary directory.
      if (passed) {
        deleteRecursively(scratch);
      } else if (keepIfFailed) {
        keptDirectories++;
      } else {
        // The test is already failing, and its failure message is more informative than anything
        // about cleaning up after it, so do not let a problem here replace that message.  A
        // subprocess that the harness killed for running too long, or a process that such a
        // subprocess started, may still be creating files under `scratch`, which would make
        // deleting `scratch` fail.
        try {
          deleteRecursively(scratch);
        } catch (IOException | RuntimeException e) {
          System.err.println("Problem deleting " + scratch + ": " + e);
        }
      }
    }
  }

  /**
   * Returns a description, for a failure message, of where the failing test's files are.
   *
   * @param home the test's temporary home directory
   * @param kept true if the directory was left in place
   * @return a description of where the failing test's files are, ending with a line separator
   */
  private static String whereFiles(Path home, boolean kept) {
    return kept
        ? "The files are in " + home + "\n"
        : "The files were in "
            + home
            + ", but were deleted because the files of "
            + maxKeptDirectories
            + " earlier failures are being kept\n";
  }

  /**
   * Compares one part of the program's behavior against a goal file. If regenerating, writes the
   * goal file instead.
   *
   * @param goalFile the goal file; it need not exist, in which case the expected value is empty
   * @param actual the program's actual behavior
   * @param what a description of what is being compared, for the failure message
   * @param context a description of the subprocess and its environment, for the failure message
   * @throws IOException if the goal file cannot be read or written
   */
  private void checkGoal(Path goalFile, String actual, String what, String context)
      throws IOException {
    if (regenerate) {
      // An exit status of 0 and an empty stream are the defaults, so represent them by the absence
      // of a goal file.
      if (actual.equals("") || (what.equals("exit status") && actual.equals("0\n"))) {
        Files.deleteIfExists(goalFile);
      } else {
        Files.writeString(goalFile, actual, UTF_8);
      }
      return;
    }
    String expected = what.equals("exit status") ? "0\n" : "";
    if (Files.exists(goalFile)) {
      expected = readFile(goalFile);
    }
    assertEquals(
        expected, actual, () -> "Wrong " + what + "; goal file is " + goalFile + "\n" + context);
  }

  /**
   * Returns a description of a subprocess and its environment, for use in a failure message.
   *
   * @param subprocess a description of the subprocess, such as {@code "The program"}
   * @param home the temporary home directory
   * @param kept true if the temporary home directory was left in place
   * @param versions the versions of the programs that the test case requires
   * @param result the subprocess's complete output
   * @return a description of the subprocess and its environment
   */
  private static String context(
      String subprocess, Path home, boolean kept, String versions, Result result) {
    return subprocess
        + " ran in the test's temporary home directory.\n"
        + whereFiles(home, kept)
        + versions
        + result.describe();
  }

  // //////////////////////////////////////////////////////////////////////
  // Running subprocesses
  //

  /**
   * The outcome of running a subprocess.
   *
   * @param status the exit status
   * @param stdout everything the subprocess printed to standard output
   * @param stderr everything the subprocess printed to standard error
   */
  private record Result(int status, String stdout, String stderr) {

    /**
     * Returns a multi-line description of this, for use in a failure message.
     *
     * @return a description of this
     */
    String describe() {
      return "exit status: "
          + status
          + "\nstandard output:\n<<<"
          + stdout
          + ">>>\nstandard error:\n<<<"
          + stderr
          + ">>>";
    }
  }

  /**
   * Runs a command in a subprocess and returns its outcome. The subprocess environment is
   * deterministic: it ignores the user's git, Mercurial, and Subversion configuration, and it uses
   * a fixed locale, time zone, and commit identity.
   *
   * @param command the command to run
   * @param home the directory to use as both the working directory and {@code $HOME}
   * @param scratch a directory in which to write the subprocess's output
   * @return the outcome of running the command
   * @throws IOException if the subprocess cannot be run
   * @throws InterruptedException if waiting for the subprocess is interrupted
   */
  private Result run(List<String> command, Path home, Path scratch)
      throws IOException, InterruptedException {
    Path outFile = scratch.resolve("stdout.txt");
    Path errFile = scratch.resolve("stderr.txt");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(home.toFile());
    pb.redirectOutput(outFile.toFile());
    pb.redirectError(errFile.toFile());
    Map<String, String> env = pb.environment();
    // Inherited settings would make the output depend on who runs the tests.  In particular, a JVM
    // whose JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, or JDK_JAVA_OPTIONS environment variable is set
    // prints a "Picked up ..." line to standard error.  Those variables are set by default in some
    // Docker images and in some corporate environments.
    env.keySet()
        .removeIf(
            k ->
                k.startsWith("GIT_")
                    || k.startsWith("HG")
                    || k.startsWith("SVN_")
                    || k.equals("JAVA_TOOL_OPTIONS")
                    || k.equals("_JAVA_OPTIONS")
                    || k.equals("JDK_JAVA_OPTIONS"));
    env.put("HOME", home.toString());
    env.put("XDG_CONFIG_HOME", home.resolve(".config").toString());
    env.put("GIT_CONFIG_GLOBAL", "/dev/null");
    env.put("GIT_CONFIG_SYSTEM", "/dev/null");
    env.put("GIT_AUTHOR_NAME", "Test User");
    env.put("GIT_AUTHOR_EMAIL", "test@example.com");
    env.put("GIT_AUTHOR_DATE", "2020-01-01T00:00:00+00:00");
    env.put("GIT_COMMITTER_NAME", "Test User");
    env.put("GIT_COMMITTER_EMAIL", "test@example.com");
    env.put("GIT_COMMITTER_DATE", "2020-01-01T00:00:00+00:00");
    env.put("HGRCPATH", "/dev/null");
    env.put("HGUSER", "Test User <test@example.com>");
    env.put("HGPLAIN", "1");
    env.put("LC_ALL", "C");
    env.put("LANG", "C");
    env.put("TZ", "UTC");
    Process process = pb.start();
    if (!process.waitFor(subprocessTimeoutSeconds, TimeUnit.SECONDS)) {
      // Wait for the subprocess to die, so that it is not still writing files under `scratch` when
      // the caller deletes that directory.  A process that the subprocess started might outlive
      // it, so the caller tolerates a failure to delete.
      process.destroyForcibly().waitFor(destroyTimeoutSeconds, TimeUnit.SECONDS);
      throw new AssertionError(
          "Killed a command that did not finish within "
              + subprocessTimeoutSeconds
              + " seconds: "
              + String.join(" ", command)
              + "\nstandard output so far:\n<<<"
              + readFile(outFile)
              + ">>>\nstandard error so far:\n<<<"
              + readFile(errFile)
              + ">>>");
    }
    return new Result(process.exitValue(), readFile(outFile), readFile(errFile));
  }

  /**
   * Returns a description of the versions of the programs that a test case requires. A goal file
   * may record the exact wording of a program's messages, so a different version of that program is
   * a common reason for a test to fail.
   *
   * @param programs the programs that the test case requires
   * @param home the directory to use as both the working directory and {@code $HOME}
   * @param scratch a directory in which to write the subprocesses' output
   * @return a description of the programs' versions, ending with a line separator, or {@code ""}
   * @throws IOException if a subprocess cannot be run
   * @throws InterruptedException if waiting for a subprocess is interrupted
   */
  private String versionsOf(List<String> programs, Path home, Path scratch)
      throws IOException, InterruptedException {
    StringBuilder result = new StringBuilder();
    for (String program : programs) {
      Result version = run(List.of(program, "--version"), home, scratch);
      result
          .append("Version of ")
          .append(program)
          .append(": ")
          .append(version.stdout().lines().findFirst().orElse("unknown"))
          .append("\n");
    }
    return result.toString();
  }

  /**
   * Returns true if the given program is on the PATH.
   *
   * @param program the name of a program
   * @return true if the program is on the PATH
   */
  private static boolean onPath(String program) {
    String path = System.getenv("PATH");
    if (path == null) {
      return false;
    }
    for (String dir : path.split(java.io.File.pathSeparator, -1)) {
      if (Files.isExecutable(Path.of(dir, program))) {
        return true;
      }
    }
    return false;
  }

  // //////////////////////////////////////////////////////////////////////
  // Reading test case files
  //

  /**
   * Returns the command-line arguments for a test case.
   *
   * @param caseDir the test case directory
   * @return the command-line arguments, before substitution of {@code ${HOME}}
   * @throws IOException if the {@code args} file cannot be read
   */
  private static List<String> arguments(Path caseDir) throws IOException {
    Path argsFile = caseDir.resolve("args");
    if (!Files.exists(argsFile)) {
      throw new AssertionError("No `args` file in " + caseDir);
    }
    List<String> result = new ArrayList<>();
    for (String line : readFile(argsFile).split("\n", -1)) {
      String trimmed = line.trim();
      if (trimmed.equals("") || trimmed.startsWith("#")) {
        continue;
      }
      result.add(trimmed.equals("\"\"") ? "" : trimmed);
    }
    return result;
  }

  /**
   * Returns the name of a test case, which is the last element of its directory's path.
   *
   * @param caseDir a test case directory
   * @return the name of the test case
   */
  private static String caseName(Path caseDir) {
    Path fileName = caseDir.getFileName();
    if (fileName == null) {
      throw new AssertionError("Test case directory has no name: " + caseDir);
    }
    return fileName.toString();
  }

  /**
   * Returns the contents of a file.
   *
   * @param file a file
   * @return the contents of the file
   * @throws IOException if the file cannot be read
   */
  private static String readFile(Path file) throws IOException {
    return Files.readString(file, UTF_8);
  }

  /**
   * Returns the contents of a file, or the empty string if the file does not exist.
   *
   * @param file a file that might not exist
   * @return the contents of the file, or ""
   * @throws IOException if the file exists but cannot be read
   */
  private static String readFileOrEmpty(Path file) throws IOException {
    return Files.exists(file) ? readFile(file) : "";
  }

  /**
   * Splits a string on whitespace, discarding empty results.
   *
   * @param s a string
   * @return the whitespace-separated words of the string
   */
  private static List<String> whitespaceSeparated(String s) {
    List<String> result = new ArrayList<>();
    for (String word : s.split("\\s+", -1)) {
      if (!word.equals("")) {
        result.add(word);
      }
    }
    return result;
  }

  // //////////////////////////////////////////////////////////////////////
  // Normalizing output
  //

  /**
   * Replaces {@code ${HOME}} by the temporary home directory.
   *
   * @param s a string from a test case file
   * @param home the temporary home directory
   * @return the string, with {@code ${HOME}} replaced
   */
  private static String substituteHome(String s, Path home) {
    return s.replace("${HOME}", home.toString());
  }

  /**
   * Makes the program's output independent of where the test happens to run: replaces the temporary
   * home directory by {@code ${HOME}}, replaces the home directory of the user who is running the
   * tests by {@code ${USER_HOME}}, and normalizes line separators.
   *
   * <p>The usage message mentions the user's real home directory even though the program is run
   * with {@code --home}, because it prints each option's default rather than its current value.
   *
   * @param s the program's output
   * @param home the temporary home directory
   * @return the output, in a form that can be compared against a goal file
   */
  private static String normalize(String s, Path home) {
    String result = s.replace("\r\n", "\n");
    result = result.replace(home.toString(), "${HOME}");
    String userHome = System.getProperty("user.home");
    // The JVM sets `user.home` to "?" when it cannot determine the home directory, as when a
    // container runs as a uid that has no entry in the password database.  Substituting for that
    // would replace every question mark in the program's output.
    if (userHome != null && userHome.length() > 1) {
      result = result.replace(userHome, "${USER_HOME}");
    }
    return result;
  }

  /**
   * Like {@link #normalize}, but also removes the warnings that the JVM prints. Only standard error
   * is filtered this way, because a line of the program's own standard output might start with
   * "WARNING: ".
   *
   * @param s the program's standard error
   * @param home the temporary home directory
   * @return the standard error, in a form that can be compared against a goal file
   */
  private static String normalizeStderr(String s, Path home) {
    return jvmWarnings.matcher(normalize(s, home)).replaceAll("");
  }

  /**
   * Returns the lines of a string, sorted. A trailing line separator, if there is one, stays at the
   * end rather than being sorted along with the text.
   *
   * @param s a string
   * @return the lines of the string, sorted
   */
  private static String sortLines(String s) {
    if (s.equals("")) {
      return s;
    }
    boolean endsWithSeparator = s.endsWith("\n");
    List<String> lines = new ArrayList<>(Arrays.asList(s.split("\n", -1)));
    if (endsWithSeparator) {
      // `split` produces a final empty element for the trailing line separator.
      lines.remove(lines.size() - 1);
    }
    lines.sort(Comparator.naturalOrder());
    String result = String.join("\n", lines);
    return endsWithSeparator ? result + "\n" : result;
  }

  // //////////////////////////////////////////////////////////////////////
  // File system utilities
  //

  /**
   * Deletes a directory and everything in it.
   *
   * @param dir the directory to delete
   * @throws IOException if the directory cannot be deleted
   */
  private static void deleteRecursively(Path dir) throws IOException {
    List<Path> paths;
    try (Stream<Path> walk = Files.walk(dir)) {
      // Deepest first, so that a directory is deleted after its contents.
      paths = walk.sorted(Comparator.reverseOrder()).toList();
    }
    for (Path path : paths) {
      Files.delete(path);
    }
  }
}
