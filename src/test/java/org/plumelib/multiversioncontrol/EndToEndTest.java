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
 *       test is skipped rather than failed.
 *   <dt>{@code sort-output}
 *   <dd>If this (possibly empty) file exists, the lines of standard output are sorted before
 *       comparison. Use this only for a test whose output order the program does not define.
 *   <dt>{@code notes}
 *   <dd>Commentary for human readers; the test harness ignores it.
 * </dl>
 *
 * <p>Absolute pathnames vary from run to run, so before comparing the program's output to a goal
 * file, the temporary home directory is replaced by {@code ${HOME}} and the home directory of the
 * user who is running the tests is replaced by {@code ${USER_HOME}}.
 *
 * <p>To overwrite the goal files with the program's current output, run {@code ./gradlew test
 * -Pregenerate}. Always inspect the resulting diffs: a goal file should record what the program
 * ought to print, not merely what it does print.
 */
final class EndToEndTest {

  /** Creates a new EndToEndTest. */
  EndToEndTest() {}

  /** The directory that contains the test case directories. */
  private static final Path casesDir =
      Path.of(getRequiredProperty("mvc.test.casesDir", "src/test/resources/e2e"));

  /** The class path with which to run the program. */
  private static final String classpath =
      getRequiredProperty("mvc.test.classpath", System.getProperty("java.class.path"));

  /** If true, overwrite the goal files instead of just comparing against them. */
  private static final boolean regenerate = Boolean.getBoolean("mvc.test.regenerate");

  /** The {@code java} executable that runs the program. */
  private static final String javaExecutable =
      Path.of(System.getProperty("java.home"), "bin", "java").toString();

  /**
   * The JVM argument that runs the code coverage agent, or the empty string if coverage is not
   * being measured. The program runs in a subprocess, so the agent must run there.
   */
  private static final String jacocoArg = getRequiredProperty("mvc.test.jacocoArg", "");

  /**
   * Matches the warnings that the JVM prints, on some JDK versions, when SVNKit loads its native
   * library. Whether they appear depends on the JDK version, not on the program, so remove them.
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
  private static String getRequiredProperty(String property, String defaultValue) {
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
   * Runs one test case: run the program, then compare its output to the goal files.
   *
   * @param caseDir the test case directory
   * @throws IOException if a file cannot be read or written
   * @throws InterruptedException if a subprocess is interrupted
   */
  private void runTestCase(Path caseDir) throws IOException, InterruptedException {
    String caseName = caseName(caseDir);
    for (String program : whitespaceSeparated(readFileOrEmpty(caseDir.resolve("requires")))) {
      Assumptions.assumeTrue(
          onPath(program), "Skipping " + caseName + ": no " + program + " on PATH");
    }

    Path scratch = Files.createTempDirectory("mvc-e2e-").toRealPath();
    boolean passed = false;
    try {
      Path home = Files.createDirectory(scratch.resolve("home"));

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
                  + "; files are in "
                  + home
                  + "\n"
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

      String stdout = normalize(result.stdout(), home);
      if (Files.exists(caseDir.resolve("sort-output"))) {
        stdout = sortLines(stdout);
      }
      checkGoal(caseDir.resolve("expected-out"), stdout, "standard output", result, home);
      checkGoal(
          caseDir.resolve("expected-err"),
          normalize(result.stderr(), home),
          "standard error",
          result,
          home);
      checkGoal(
          caseDir.resolve("expected-status"), result.status() + "\n", "exit status", result, home);

      Path postcheckScript = caseDir.resolve("postcheck.sh");
      if (Files.exists(postcheckScript)) {
        Result postcheck =
            run(List.of("bash", postcheckScript.toAbsolutePath().toString()), home, scratch);
        if (postcheck.status() != 0) {
          throw new AssertionError(
              "postcheck.sh failed for "
                  + caseName
                  + "; files are in "
                  + home
                  + "\n"
                  + postcheck.describe());
        }
        checkGoal(
            caseDir.resolve("expected-postcheck"),
            normalize(postcheck.stdout(), home),
            "postcheck output",
            result,
            home);
      }
      passed = true;
    } finally {
      // On failure, leave the files in place; the failure message says where they are.
      if (passed) {
        deleteRecursively(scratch);
      }
    }
  }

  /**
   * Compares one part of the program's behavior against a goal file. If regenerating, writes the
   * goal file instead.
   *
   * @param goalFile the goal file; it need not exist, in which case the expected value is empty
   * @param actual the program's actual behavior
   * @param what a description of what is being compared, for the failure message
   * @param result the program's complete output, for the failure message
   * @param home the temporary home directory, which is left in place if the test fails
   * @throws IOException if the goal file cannot be read or written
   */
  private void checkGoal(Path goalFile, String actual, String what, Result result, Path home)
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
        expected,
        actual,
        () ->
            "Wrong "
                + what
                + "; goal file is "
                + goalFile
                + "\nThe program ran in "
                + home
                + ", which was left in place\n"
                + result.describe());
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
    // Inherited settings would make the output depend on who runs the tests.
    env.keySet().removeIf(k -> k.startsWith("GIT_") || k.startsWith("HG") || k.startsWith("SVN_"));
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
    int status = process.waitFor();
    return new Result(status, readFile(outFile), readFile(errFile));
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
   * tests by {@code ${USER_HOME}}, normalizes line separators, and removes JVM warnings.
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
    if (userHome != null && !userHome.equals("")) {
      result = result.replace(userHome, "${USER_HOME}");
    }
    result = jvmWarnings.matcher(result).replaceAll("");
    return result;
  }

  /**
   * Returns the lines of a string, sorted.
   *
   * @param s a string that ends with a line separator, or is empty
   * @return the lines of the string, sorted
   */
  private static String sortLines(String s) {
    if (s.equals("")) {
      return s;
    }
    List<String> lines = new ArrayList<>(Arrays.asList(s.split("\n", -1)));
    // `split` produces a final empty element for the trailing line separator.
    String last = lines.remove(lines.size() - 1);
    lines.sort(Comparator.naturalOrder());
    lines.add(last);
    return String.join("\n", lines);
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
