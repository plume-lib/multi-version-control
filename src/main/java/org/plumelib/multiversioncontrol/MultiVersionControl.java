package org.plumelib.multiversioncontrol;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.modifiability.qual.Growable;
import org.checkerframework.checker.modifiability.qual.IteratorPolyMod;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.checker.regex.qual.Regex;
import org.checkerframework.checker.signedness.qual.PolySigned;
import org.checkerframework.common.initializedfields.qual.EnsuresInitializedFields;
import org.checkerframework.common.value.qual.MinLen;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.plumelib.options.Option;
import org.plumelib.options.OptionGroup;
import org.plumelib.options.Options;
import org.plumelib.util.EntryReader;
import org.plumelib.util.EntryReader.CommentFormat;
import org.plumelib.util.EntryReader.EntryFormat;
import org.plumelib.util.FilesP;
import org.plumelib.util.StringsP;
import org.plumelib.util.UtilP;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

// A related program is the "mr" program (http://kitenet.net/~joey/code/mr/).
// To read its documentation:  pod2man mr | nroff -man
// Some differences are:
//  * mvc knows how to search for all repositories
//  * mvc uses a timeout
//  * mvc tries to improve tool output:
//     * mvc tries to be as quiet as possible.  The fact that it issues
//       output only if there is a problem makes "mvc status" appropriate
//       for running as a cron job, and reduces distraction.
//     * mvc rewrites paths from relative to absolute form or adds
//       pathnames, to make output comprehensible without knowing the
//       working directory of the command.
//  * mvc's configuration files tend to be smaller & simpler

/**
 * This program simplifies managing your clones/checkouts. This program lets you run a version
 * control command, such as "status" or "pull", on a <b>set</b> of CVS/Git/Hg/SVN clones/checkouts
 * rather than just one. You might want to pull/update all of them, or you might want to know
 * whether any of them have uncommitted changes. When setting up a new account, you might want to
 * clone them all. This program does those tasks.
 *
 * <p>You can specify the set of clones for the program to manage in a file {@code .mvc-checkouts},
 * or you can pass {@code --search} to make the program search your directory structure to find all
 * of your clones. For example (assuming you have a <a href="#installation">{@code mvc} alias</a>),
 * to list all uncommitted changed files under your home directory:
 *
 * <pre>
 * mvc status --search=true</pre>
 *
 * <p>This program accepts these arguments:
 *
 * <pre>
 *   clone     -- Clone (check out) all repositories.
 *   checkout  -- Same as clone.
 *   pull      -- Pull and update all clones.
 *   update    -- Same as pull.
 *   status    -- Show files that are changed but not committed, or committed
 *                but not pushed, or have shelved/stashed changes.
 *   list      -- List the clones/checkouts that this program is aware of.
 * </pre>
 *
 * <p>For a git clone, "status" reports a local branch whose changes are on no remote. A branch's
 * changes are on a remote if a remote branch contains its commits, or if the changes are in a
 * remote's main branch ({@code HEAD}, {@code master}, or {@code main}), as after the branch has
 * been squash-merged. The changes are also on a remote if a fetched pull-request head contains the
 * branch's commits and the pull request's changes are in a remote's main branch, as after the pull
 * request has been squash-merged with commits that the clone never fetched. Determining whether
 * changes are in a remote's main branch requires Git 2.40 or later.
 *
 * <p>For a git clone, "pull" fetches pull-request heads (such as GitHub's {@code refs/pull/N/head},
 * which GitHub keeps even after a pull request's branch has been deleted) into {@code
 * refs/pull/REMOTE/N}. It does so only if some local branch has commits that are on no remote, and
 * then only for a remote on GitHub. It fetches only the pull requests whose head commit the clone
 * has and is on no remote branch, or whose head it fetched before. It deletes a ref under {@code
 * refs/pull/} whose remote is not a GitHub remote of the clone. A fork's pull requests are in the
 * repository that it was forked from, so a clone of a fork needs a remote for that repository.
 *
 * <p>(The {@code commit} action is not supported, because that is not something that should be done
 * in an automated way &mdash; it needs a user-written commit message.)
 *
 * <p><b>Command-line arguments</b>
 *
 * <p>The command-line options are as follows:
 * <!-- spotless:off -->
 * <!-- start options doc (DO NOT EDIT BY HAND) -->
 *
 * <ul>
 *   <li id="optiongroup:Configuration-file">Configuration file
 *                                           <ul>
 *                                             <li id="option:home"><b>--home=</b><i>string</i>.
 *                                                                  User home directory. [default
 *                                                                  Java {@code user.home}
 *                                                                  property].
 *                                             <li id="option:checkouts"><b>--checkouts=</b><i>string</i>.
 *                                                                       File with list of clones.
 *                                                                       Set it to /dev/null to
 *                                                                       suppress reading. [default
 *                                                                       {@code .mvc-checkouts} in
 *                                                                       home directory]
 *                                           </ul>
 *   <li id="optiongroup:Miscellaneous-options">Miscellaneous options
 *                                              <ul>
 *                                                <li id="option:redo-existing"><b>--redo-existing=</b><i>boolean</i>.
 *                                                                              If false, clone
 *                                                                              command skips
 *                                                                              existing
 *                                                                              directories.
 *                                                                              [default: false]
 *                                                <li id="option:timeout"><b>--timeout=</b><i>int</i>.
 *                                                                        Terminating the process
 *                                                                        can leave the repository
 *                                                                        in a bad state, so set
 *                                                                        this rather high for
 *                                                                        safety. Also, the timeout
 *                                                                        needs to account for the
 *                                                                        time to run hooks (that
 *                                                                        might recompile or run
 *                                                                        tests). [default: 600]
 *                                              </ul>
 *   <li id="optiongroup:Searching-for-clones">Searching for clones
 *                                             <ul>
 *                                               <li id="option:search"><b>--search=</b><i>boolean</i>.
 *                                                                      If true, search for all
 *                                                                      clones, not just those
 *                                                                      listed in a file. [default:
 *                                                                      false]
 *                                               <li id="option:search-prefix"><b>--search-prefix=</b><i>boolean</i>.
 *                                                                             If true, search for
 *                                                                             all clones whose
 *                                                                             directory is a prefix
 *                                                                             of one in the
 *                                                                             configuration file.
 *                                                                             This is especially
 *                                                                             useful when working
 *                                                                             with <a
 *                                                                             href="https://github.com/plume-lib/manage-git-branches">manage-git-branches</a>.
 *                                                                             [default: false]
 *                                               <li id="option:dir"><b>--dir=</b><i>string</i>
 *                                                                   {@code [+]}. Directory under
 *                                                                   which to search for clones,
 *                                                                   when using {@code --search}
 *                                                                   [default = home directory].
 *                                               <li id="option:ignore-dir"><b>--ignore-dir=</b><i>string</i>
 *                                                                          {@code [+]}. Directories
 *                                                                          under which to NOT
 *                                                                          search for clones. May
 *                                                                          include leading "~/".
 *                                             </ul>
 *   <li id="optiongroup:Paths-to-programs">Paths to programs
 *                                          <ul>
 *                                            <li id="option:cvs-executable"><b>--cvs-executable=</b><i>string</i>.
 *                                                                           Path to the cvs
 *                                                                           program. [default: cvs]
 *                                            <li id="option:git-executable"><b>--git-executable=</b><i>string</i>.
 *                                                                           Path to the git
 *                                                                           program. [default: git]
 *                                            <li id="option:hg-executable"><b>--hg-executable=</b><i>string</i>.
 *                                                                          Path to the hg program.
 *                                                                          [default: hg]
 *                                            <li id="option:svn-executable"><b>--svn-executable=</b><i>string</i>.
 *                                                                           Path to the svn
 *                                                                           program. [default: svn]
 *                                            <li id="option:insecure"><b>--insecure=</b><i>boolean</i>.
 *                                                                     If true, use --insecure when
 *                                                                     invoking programs. [default:
 *                                                                     false]
 *                                            <li id="option:cvs-arg"><b>--cvs-arg=</b><i>string</i>
 *                                                                    {@code [+]}. Extra argument to
 *                                                                    pass to the cvs program.
 *                                            <li id="option:git-arg"><b>--git-arg=</b><i>string</i>
 *                                                                    {@code [+]}. Extra argument to
 *                                                                    pass to the git program.
 *                                            <li id="option:hg-arg"><b>--hg-arg=</b><i>string</i>
 *                                                                   {@code [+]}. Extra argument to
 *                                                                   pass to the hg program.
 *                                            <li id="option:svn-arg"><b>--svn-arg=</b><i>string</i>
 *                                                                    {@code [+]}. Extra argument to
 *                                                                    pass to the svn program.
 *                                          </ul>
 *   <li id="optiongroup:Diagnostics">Diagnostics
 *                                    <ul>
 *                                      <li id="option:show"><b>--show=</b><i>boolean</i>. If true,
 *                                                           display each command as it is executed.
 *                                                           [default: false]
 *                                      <li id="option:print-directory"><b>--print-directory=</b><i>boolean</i>.
 *                                                                      If true, print the directory
 *                                                                      (and the origin URL) before
 *                                                                      executing commands in it.
 *                                                                      [default: false]
 *                                      <li id="option:dry-run"><b>--dry-run=</b><i>boolean</i>.
 *                                                              Perform a "dry run": print commands
 *                                                              but do not execute them. [default:
 *                                                              false]
 *                                      <li id="option:quiet"><b>-q</b>
 *                                                            <b>--quiet=</b><i>boolean</i>. If
 *                                                            true, run quietly (e.g., no output
 *                                                            about missing directories). [default:
 *                                                            true]
 *                                      <li id="option:debug"><b>--debug=</b><i>boolean</i>. Print
 *                                                            debugging output. [default: false]
 *                                      <li id="option:debug-replacers"><b>--debug-replacers=</b><i>boolean</i>.
 *                                                                      Debug 'replacers' that
 *                                                                      filter command output.
 *                                                                      [default: false]
 *                                      <li id="option:debug-process-output"><b>--debug-process-output=</b><i>boolean</i>.
 *                                                                           Lightweight debugging
 *                                                                           of 'replacers' that
 *                                                                           filter command output.
 *                                                                           [default: false]
 *                                    </ul>
 * </ul>
 *
 * {@code [+]} means option can be specified multiple times
 * <!-- end options doc -->
 * <!-- spotless:on -->
 *
 * <p><b>File format for {@code .mvc-checkouts} file</b>
 *
 * <p>The remainder of this document describes the file format for the {@code .mvc-checkouts} file.
 *
 * <p>(Note: because mvc can search for all checkouts in your directory, you don't need a {@code
 * .mvc-checkouts} file. Using a {@code .mvc-checkouts} file makes the program faster because it
 * does not have to search all of your directories. It also permits you to process only a certain
 * set of checkouts.)
 *
 * <p>The {@code .mvc-checkouts} file contains a list of <em>sections</em>. Each section names
 * either a root from which a sub-part (e.g., a module or a subdirectory) will be checked out, or a
 * repository all of which will be checked out. Examples include:
 *
 * <pre>
 * CVSROOT: :ext:login.csail.mit.edu:/afs/csail.mit.edu/u/m/mernst/.CVS/.CVS-mernst
 * SVNROOT: svn+ssh://tricycle.cs.washington.edu/cse/courses/cse403/09sp
 * SVNREPOS: svn+ssh://login.csail.mit.edu/afs/csail/u/a/user/.SVN/papers/parameterize-paper/trunk
 * HGREPOS: https://jsr308-langtools.googlecode.com/hg</pre>
 *
 * <p>Within each section is a list of directories that contain a checkout from that repository. If
 * the section names a root, then a module or subdirectory is needed. By default, the directory's
 * basename is used. This can be overridden by specifying the module/subdirectory on the same line,
 * after a space. If the section names a repository, then no module information is needed or used.
 *
 * <p>When performing a checkout, the parent directories are created if needed.
 *
 * <p>In the file, blank lines, and lines beginning with "#", are ignored.
 *
 * <p>Here are some example sections:
 *
 * <pre>
 * CVSROOT: :ext:login.csail.mit.edu:/afs/csail.mit.edu/group/pag/projects/classify-tests/.CVS
 * ~/research/testing/symstra-eclat-paper
 * ~/research/testing/symstra-eclat-code
 * ~/research/testing/eclat
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/.SVNREPOS/
 * ~/research/typequals/igj
 * ~/research/typequals/annotations-papers
 *
 * SVNREPOS: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/abb/REPOS
 * ~/prof/grants/2008-06-abb/abb
 *
 * HGREPOS: https://checker-framework.googlecode.com/hg/
 * ~/research/types/checker-framework
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/u/d/dannydig/REPOS/
 * ~/research/concurrency/concurrentPaper
 * ~/research/concurrency/mit.edu.refactorings concRefactor/project/mit.edu.refactorings</pre>
 *
 * <p>Furthermore, these 2 sections have identical effects:
 *
 * <pre>
 * SVNROOT: https://crashma.googlecode.com/svn/
 * ~/research/crashma trunk
 *
 * SVNREPOS: https://crashma.googlecode.com/svn/trunk
 * ~/research/crashma</pre>
 *
 * <p>and, all 3 of these sections have identical effects:
 *
 * <pre>
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/
 * ~/research/typequals/annotations
 *
 * SVNROOT: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/
 * ~/research/typequals/annotations annotations
 *
 * SVNREPOS: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/annotations
 * ~/research/typequals/annotations</pre>
 *
 * <p id="installation"><b>Installation</b>
 *
 * <pre>
 * git clone https://github.com/plume-lib/multi-version-control
 * cd multi-version-control
 * ./gradlew shadowJar
 *
 * alias mvc='java -ea -cp CURRENT_DIR/build/libs/multi-version-control-all.jar org.plumelib.multiversioncontrol.MultiVersionControl'
 * </pre>
 */

// TODO:

// It might be nice to list all the "unexpected" checkouts -- those found
// on disk that are not in the checkouts file.  This permits the checkouts
// file to be updated and then used in preference to searching the whole
// filesystem, which may be slow.
// You can do this from the command line by comparing the output of these
// two commands:
//   mvc list --repositories /dev/null | sort > checkouts-from-directory
//   mvc list --search=false | sort > checkouts-from-file
// but it might be nicer for the "list" command to do that explicitly.

// The "list" command should be in the .mvc-checkouts file format, rather
// than requiring the user to munge it.

// In checkouts file, use of space delimiter for specifying module interacts
// badly with file names that contain spaces.  This doesn't seem important
// enough to fix.

// When discovering checkouts from a directory structure, there is a
// problem when two modules from the same SVN repository are checked out,
// with one checkout inside the other at the top level.  The inner
// checkout's directory can be mis-reported as the outer one.  This isn't
// always a problem for nested checkouts (so it's hard to reproduce), and
// nested checkouts are bad style anyway, so I am deferring
// investigating/fixing it.

// Add "incoming" command that shows you need to do update and/or fetch?
//
// For Mercurial, I can do "hg incoming", but how to show that the current
// working directory is not up to date with respect to the local
// repository?  "hg prompt" with the "update" tag will do the trick, see
// http://bitbucket.org/sjl/hg-prompt/src/ .  Or don't bother:  it's rarely an
// issue if you always update via "hg fetch" as done by this program.
//
// For svn, "svn status -u":
//   The out-of-date information appears in the ninth column (with -u):
//       '*' a newer revision exists on the server
//       ' ' the working copy is up to date

public class MultiVersionControl {

  /** User home directory. [default Java {@code user.home} property]. */
  @OptionGroup("Configuration file")
  @Option(value = "User home directory.", noDocDefault = true)
  public static String home = System.getProperty("user.home");

  /**
   * File with list of clones. Set it to /dev/null to suppress reading. [default {@code
   * .mvc-checkouts} in home directory]
   */
  @Option(
      value = "File with list of clones.  Set it to /dev/null to suppress reading.",
      noDocDefault = true)
  public String checkouts = "~/.mvc-checkouts";

  /** If false, clone command skips existing directories. */
  @OptionGroup("Miscellaneous options")
  @Option("Redo existing clones; relevant only to clone command")
  public boolean redoExisting = false;

  /**
   * Terminating the process can leave the repository in a bad state, so set this rather high for
   * safety. Also, the timeout needs to account for the time to run hooks (that might recompile or
   * run tests).
   */
  @Option("Timeout for each command, in seconds")
  public int timeout = 600;

  // Default is false because searching the whole directory structure is slow.
  /** If true, search for all clones, not just those listed in a file. */
  @OptionGroup("Searching for clones")
  @Option("Search for all clones, not just those listed in a file")
  public boolean search = false;

  /**
   * If true, search for all clones whose directory is a prefix of one in the configuration file.
   * This is especially useful when working with <a
   * href="https://github.com/plume-lib/manage-git-branches">manage-git-branches</a>.
   */
  @Option("Search for all clones whose directory is a prefix of one listed in a file")
  public boolean searchPrefix = false;

  /**
   * Directory under which to search for clones, when using {@code --search} [default = home
   * directory].
   */
  @Option("Directory under which to search for clones; default=home dir")
  public @Modifiable @IteratorPolyMod List<String> dir = new ArrayList<>();

  /** Directories under which to NOT search for clones. May include leading "~/". */
  @Option("Directory under which to NOT search for clones")
  public List<String> ignoreDir = new ArrayList<>();

  /** Files, each a directory, corresponding to strings in {@link ignoreDir}. */
  private @Modifiable Set<File> ignoreDirs = new LinkedHashSet<>();

  // These *-executable command-line options are handy:
  //  * if you want to use a specific version of the program
  //  * if the program is not on your path
  //  * if there is a directory of the same name as the program, and . is on
  //    your path; in that case, the command would try to execute the directory.

  /** Path to the cvs program. */
  @OptionGroup("Paths to programs")
  @Option("Path to the cvs program")
  public String cvsExecutable = "cvs";

  /** Path to the git program. */
  @Option("Path to the git program")
  public String gitExecutable = "git";

  /** Path to the hg program. */
  @Option("Path to the hg program")
  public String hgExecutable = "hg";

  /** Path to the svn program. */
  @Option("Path to the svn program")
  public String svnExecutable = "svn";

  /** If true, use --insecure when invoking programs. */
  @Option("Pass --insecure argument to hg")
  public boolean insecure = false;

  // The {cvs,git,hg,svn}_arg options probably aren't very useful, because
  // there are few arguments that are applicable to every command; for
  // example, --insecure isn't applicable to "hg status".

  /** Extra argument to pass to the cvs program. */
  @Option("Extra argument to pass to the cvs program")
  public List<String> cvsArg = new ArrayList<>();

  /** Extra argument to pass to the git program. */
  @Option("Extra argument to pass to the git program")
  public List<String> gitArg = new ArrayList<>();

  /** Extra argument to pass to the hg program. */
  @Option("Extra argument to pass to the hg program")
  public List<String> hgArg = new ArrayList<>();

  /** Extra argument to pass to the svn program. */
  @Option("Extra argument to pass to the svn program")
  public List<String> svnArg = new ArrayList<>();

  // TODO: use consistent names: both "show" or both "print"

  /** If true, display each command as it is executed. */
  @Option("Display commands as they are executed")
  @OptionGroup("Diagnostics")
  public boolean show = false;

  /** If true, print the directory (and the origin URL) before executing commands in it. */
  @Option("Print the directory before executing commands")
  public boolean printDirectory = false;

  /** Perform a "dry run": print commands but do not execute them. */
  @Option("Do not execute commands; just print them.  Implies --show --redo-existing")
  public boolean dryRun = false;

  /** If true, run quietly (e.g., no output about missing directories). */
  @Option("-q Run quietly (e.g., no output about missing directories)")
  public boolean quiet = true;

  // It would be good to be able to set this per-clone.
  // This variable is static because it is used in static methods.
  /** Print debugging output. */
  @Option("Print debugging output")
  public static boolean debug = false;

  /** Debug 'replacers' that filter command output. */
  @Option("Debug 'replacers' that filter command output")
  public boolean debugReplacers = false;

  /** Lightweight debugging of 'replacers' that filter command output. */
  @Option("Lightweight debugging of 'replacers' that filter command output")
  public boolean debugProcessOutput = false;

  /** Actions that MultiVersionControl can perform. */
  static enum Action {
    /** Clone a repository. */
    CLONE,
    /** Show the working tree status. */
    STATUS,
    /** Pull changes from upstream. */
    PULL,
    /** List the known repositories. */
    LIST
  }

  /** Which action to perform on this run of MultiVersionControl. */
  private Action action;

  /**
   * Replace "~" by the expansion of "$HOME".
   *
   * @param path the input path, which might contain "~"
   * @return path with "~" expanded
   */
  private static String expandTilde(String path) {
    return path.replaceFirst("^~", Matcher.quoteReplacement(home));
  }

  /**
   * Runs a version control command, such as "status" or "update", on a <b>set</b> of CVS/Git/Hg/SVN
   * clones rather than just one.
   *
   * @param args the command-line arguments
   * @see MultiVersionControl
   */
  public static void main(String[] args) {
    setupSvnkit();
    MultiVersionControl mvc = new MultiVersionControl(args);

    Set<Checkout> checkouts = new LinkedHashSet<>();

    try {
      readCheckouts(new File(mvc.checkouts), checkouts, mvc.searchPrefix);
    } catch (IOException e) {
      System.err.println("Problem reading file " + mvc.checkouts + ": " + e.getMessage());
    }

    if (mvc.search) {
      // Postprocess command-line arguments
      for (String adir : mvc.ignoreDir) {
        File afile = new File(expandTilde(adir));
        if (!afile.exists()) {
          System.err.printf(
              "Warning: Directory to ignore while searching for checkouts does not exist:%n  %s%n",
              adir);
        } else if (!afile.isDirectory()) {
          System.err.printf(
              "Warning: Directory to ignore while searching for checkouts is not a directory:%n"
                  + "  %s%n",
              adir);
        } else {
          mvc.ignoreDirs.add(afile);
        }
      }

      for (String adirUnexpanded : mvc.dir) {
        String adir = expandTilde(adirUnexpanded);
        if (debug) {
          System.out.println("Searching for checkouts under " + adir);
        }
        if (!new File(adir).isDirectory()) {
          System.err.printf(
              "Directory in which to search for checkouts is not a directory: %s%n", adir);
          System.exit(2);
        }
        int oldCheckouts = checkouts.size();
        findCheckouts(new File(adir), checkouts, mvc.ignoreDirs);
        if (debug) {
          System.out.printf("Searching added %d checkouts%n", checkouts.size() - oldCheckouts);
        }
      }
      System.out.flush();
    }

    if (debug) {
      System.out.printf("About to process %d checkouts:%n", checkouts.size());
      for (Checkout c : checkouts) {
        System.out.println("  " + c);
      }
      System.out.flush();
    }
    mvc.process(checkouts);
  }

  /** Set up the SVNKit library. */
  private static void setupSvnkit() {
    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();
  }

  /** Nullary constructor for use by OptionsDoclet. */
  @SuppressWarnings({
    "nullness", // initialization warning in unused constructor
    "initializedfields:contracts.postcondition" // initialization warning in unused constructor
  })
  private MultiVersionControl() {}

  /**
   * Create a MultiVersionControl instance.
   *
   * @param args the command-line arguments to MultiVersionControl
   */
  public MultiVersionControl(String[] args) {
    parseArgs(args);
  }

  /**
   * Parse the command-line arguments.
   *
   * @param args the command-line arguments
   * @see MultiVersionControl
   */
  @RequiresNonNull({"dir", "checkouts"})
  @EnsuresNonNull("action")
  @EnsuresInitializedFields(fields = "action")
  public final void parseArgs(@UnknownInitialization MultiVersionControl this, String[] args) {
    @SuppressWarnings(
        "nullness:assignment" // new C(underInit) yields @UnderInitialization; @Initialized is safe
    )
    @Initialized Options options = new Options("mvc [options] {clone,status,pull,list}", this);
    String[] remainingArgs = options.parse(true, args);
    if (remainingArgs.length != 1) {
      System.out.printf(
          "Please supply exactly one argument (found %d)%n  %s%n",
          remainingArgs.length, String.join(" ", remainingArgs));
      options.printUsage();
      System.exit(1);
    }
    String actionString = remainingArgs[0];
    if ("checkout".startsWith(actionString)) {
      action = Action.CLONE;
    } else if ("clone".startsWith(actionString)) {
      action = Action.CLONE;
    } else if ("list".startsWith(actionString)) {
      action = Action.LIST;
    } else if ("pull".startsWith(actionString)) {
      action = Action.PULL;
    } else if ("status".startsWith(actionString)) {
      action = Action.STATUS;
    } else if ("update".startsWith(actionString)) {
      action = Action.PULL;
    } else {
      System.out.printf("Unrecognized action \"%s\"", actionString);
      options.printUsage();
      System.exit(1);
    }

    // clean up options

    checkouts = expandTilde(checkouts);

    if (dir.isEmpty()) {
      dir.add(home);
    }

    if (action == Action.CLONE) {
      search = false;
      show = true;
      // Checkouts can be much slower than other operations.
      timeout = timeout * 10;

      // Set dryRun to true unless it was explicitly specified
      boolean explicitDryRun = false;
      for (String arg : args) {
        if (arg.startsWith("--dry-run") || arg.startsWith("--dryRun")) {
          explicitDryRun = true;
        }
      }
      if (!explicitDryRun) {
        if (!quiet) {
          System.out.println(
              "No --dry-run argument, so using --dry-run=true; override with --dry-run=false");
        }
        dryRun = true;
      }
    }

    if (dryRun) {
      show = true;
      redoExisting = true;
    }

    if (debug) {
      show = true;
    }
  }

  /** The types of repositories. */
  static enum RepoType {
    /** Bazaar. */
    BZR,
    /** CVS. */
    CVS,
    /** Git. */
    GIT,
    /** Mercurial. */
    HG,
    /** Subversion. */
    SVN
  }

  /** Class that represents a clone on the local file system. */
  static class Checkout {
    /** The type of repository to clone. */
    RepoType repoType;

    /** Local directory. */
    File directory;

    /** Local directory (canonical version). */
    String canonicalDirectory;

    /**
     * Non-null for CVS and SVN. May be null for distributed version control systems (Bzr, Git, Hg).
     * For distributed systems, refers to the parent repository from which this was cloned, not the
     * one here in this directory.
     *
     * <p>Most operations don't need this. It is needed for checkout, though.
     */
    @Nullable String repository;

    /**
     * Null if no module, just whole thing. Non-null for CVS and, optionally, for SVN. Null for
     * distributed version control systems (Bzr, Git, Hg).
     */
    @Nullable String module;

    /**
     * Create a Checkout.
     *
     * @param repoType the type of repository
     * @param directory where the new clone will appear
     * @throws DirectoryDoesNotExist if the directory does not exist
     */
    Checkout(RepoType repoType, File directory) throws DirectoryDoesNotExist {
      this(repoType, directory, null, null);
    }

    /**
     * Create a Checkout.
     *
     * @param repoType the type of repository
     * @param directory where the new clone will appear
     * @param repository the upstream repository
     * @param module the module that is checked out (for CVS and optionally SVN)
     * @throws DirectoryDoesNotExist if the directory does not exist
     */
    Checkout(
        RepoType repoType, File directory, @Nullable String repository, @Nullable String module)
        throws DirectoryDoesNotExist {
      // Directory might not exist if we are running the checkout command.
      // If it exists, it must be a directory.
      assert !directory.exists() || directory.isDirectory() : "Not a directory: " + directory;
      this.repoType = repoType;
      this.directory = directory;
      try {
        this.canonicalDirectory = directory.getCanonicalPath();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      this.repository = repository;
      this.module = module;
      // These asserts come at the end so that the error message can be better.
      switch (repoType) {
        case BZR -> {
          assertSubdirExists(directory, ".bzr");
          assert module == null;
        }
        case CVS -> {
          assertSubdirExists(directory, "CVS");
          assert module != null : "No module for CVS checkout at: " + directory;
        }
        case GIT -> {
          assertSubdirExists(directory, ".git");
          assert module == null;
        }
        case HG -> {
          assertSubdirExists(directory, ".hg");
          assert module == null;
        }
        case SVN -> {
          assertSubdirExists(directory, ".svn");
          assert module == null;
        }
        default -> {
          assert false;
        }
      }
    }

    /**
     * If the directory exists, then the subdirectory must exist too.
     *
     * @param directory the directory
     * @param subdirName a subdirectory that must exist, if {@code directory} exists
     * @throws DirectoryDoesNotExist if the directory does not exist
     */
    private static void assertSubdirExists(File directory, String subdirName)
        throws DirectoryDoesNotExist {
      if (directory.exists() && !new File(directory, subdirName).isDirectory()) {
        throw new DirectoryDoesNotExist(
            String.format(
                "Directory %s exists but %s subdirectory does not exist%n", directory, subdirName));
      }
    }

    @Override
    @Pure
    public boolean equals(@GuardSatisfied Checkout this, @GuardSatisfied @Nullable Object o) {
      if (!(o instanceof Checkout other)) {
        return false;
      }
      return repoType == other.repoType
          && canonicalDirectory.equals(other.canonicalDirectory)
          && Objects.equals(module, other.module);
    }

    @Override
    @Pure
    public int hashCode(@GuardSatisfied Checkout this) {
      return Objects.hash(repoType, canonicalDirectory, module);
    }

    @Override
    @SideEffectFree
    public String toString(@GuardSatisfied Checkout this) {
      return repoType + " " + directory + " " + repository + " " + module;
    }
  }

  /** An exception indicating a version control directory (such as .git) does not exist. */
  static class DirectoryDoesNotExist extends IOException {

    /** Unique identifier for serialization. If you add or remove fields, change this number. */
    static final long serialVersionUID = 20191205;

    /**
     * Create a new DirectoryDoesNotExist.
     *
     * @param msg a message about the missing directory
     */
    DirectoryDoesNotExist(String msg) {
      super(msg);
    }
  }

  // //////////////////////////////////////////////////////////////////////
  // Read checkouts from a file
  //

  /**
   * Read checkouts from the file (in {@code .mvc-checkouts} format), and add them to the set.
   *
   * @param file the .mvc-checkouts file
   * @param checkouts the set to populate; is side-effected by this method, but only if the whole
   *     file was read successfully
   * @param searchPrefix if true, search for all clones whose directory is a prefix of one in the
   *     configuration file
   * @throws IOException if there is trouble reading the file (or file system?)
   */
  static void readCheckouts(File file, @Growable Set<Checkout> checkouts, boolean searchPrefix)
      throws IOException {
    RepoType currentType = RepoType.BZR; // arbitrary choice, to avoid uninitialized variable
    String currentRoot = null;
    boolean currentRootIsRepos = false;

    // Accumulate into a temporary set, so that if reading the file fails partway through, the
    // caller's set is not left holding a partial (and therefore misleading) configuration.
    Set<Checkout> fileCheckouts = new LinkedHashSet<>();

    try (EntryReader er = new EntryReader(file)) {
      for (String lineUntrimmed : er) {
        if (debug) {
          System.out.println("line: " + lineUntrimmed);
        }
        String line = lineUntrimmed.trim();
        // Skip comments and blank lines
        if (line.equals("") || line.startsWith("#")) {
          continue;
        }

        String[] splitTwo = line.split("[ \t]+", -1);
        if (debug) {
          System.out.println("split length: " + splitTwo.length);
        }
        if (splitTwo.length == 2) {
          String word1 = splitTwo[0];
          String word2 = splitTwo[1];
          if (word1.equals("BZRROOT:") || word1.equals("BZRREPOS:")) {
            currentType = RepoType.BZR;
            currentRoot = word2;
            currentRootIsRepos = word1.equals("BZRREPOS:");
            continue;
          } else if (word1.equals("CVSROOT:")) {
            currentType = RepoType.CVS;
            currentRoot = word2;
            currentRootIsRepos = false;
            // If the CVSROOT is remote, try to make it local.
            if (currentRoot.startsWith(":ext:")) {
              String[] rootWords = currentRoot.split(":", -1);
              String possibleRoot = rootWords[rootWords.length - 1];
              if (new File(possibleRoot).isDirectory()) {
                currentRoot = possibleRoot;
              }
            }
            continue;
          } else if (word1.equals("HGROOT:") || word1.equals("HGREPOS:")) {
            currentType = RepoType.HG;
            currentRoot = word2;
            currentRootIsRepos = word1.equals("HGREPOS:");
            continue;
          } else if (word1.equals("GITROOT:") || word1.equals("GITREPOS:")) {
            currentType = RepoType.GIT;
            currentRoot = word2;
            currentRootIsRepos = word1.equals("GITREPOS:");
            continue;
          } else if (word1.equals("SVNROOT:") || word1.equals("SVNREPOS:")) {
            currentType = RepoType.SVN;
            currentRoot = word2;
            currentRootIsRepos = word1.equals("SVNREPOS:");
            continue;
          }
        }

        if (currentRoot == null) {
          System.err.printf(
              "need root before directory at line %d of file %s%n",
              er.getLineNumber(), er.getFileName());
          System.exit(1);
        }

        String dirname;
        String root = StringsP.replaceSuffix(currentRoot, "/", "");
        String module = null;

        int spacePos = line.lastIndexOf(' ');
        if (spacePos == -1) {
          dirname = line;
        } else {
          dirname = line.substring(0, spacePos);
          module = line.substring(spacePos + 1);
        }

        // The directory may not yet exist if we are doing a checkout.
        File dir = new File(expandTilde(dirname));

        if (module == null) {
          module = dir.getName();
        }
        if (currentType != RepoType.CVS) {
          if (!currentRootIsRepos) {
            root = root + "/" + module;
          }
          module = null;
        }

        Checkout checkout = new Checkout(currentType, dir, root, module);
        fileCheckouts.add(checkout);

        // TODO: This can result in near-duplicates in the checkouts set.  Suppose that the
        // .mvc-checkouts file contains two lines
        //   /a/b/c
        //   /a/b/c-fork-d
        // with different repositories, and there exists a directory
        //   /a/b/c-fork-d-branch-e
        // Then the latter is included twice, once each with the repository of `c` and of
        // `c-fork-d`.
        if (searchPrefix) {
          String dirName = dir.getName();
          File dirParent = dir.getParentFile();
          if (dirParent == null || !dirParent.isDirectory()) {
            continue;
          }
          File[] siblings =
              dirParent.listFiles(f -> f.isDirectory() && f.getName().startsWith(dirName));
          if (siblings == null) {
            throw new Error(
                String.format(
                    "This cannot happen, because %s (parent of %s) is a directory",
                    dirParent, dir));
          }
          for (File sibling : siblings) {
            try {
              fileCheckouts.add(new Checkout(currentType, sibling, root, module));
            } catch (DirectoryDoesNotExist e) {
              // A directory is an extension of a file in
              // .mvc-checkouts, but lacks a (e.g.) .git subdir.  Just
              // skip that directory.
            }
          }
        }
      }
    }
    // Any IOException propagates to the caller (main), which reports it and continues.  Because the
    // merge below has not happened yet in that case, the caller never processes a partially read
    // configuration file.
    checkouts.addAll(fileCheckouts);

    if (debug) {
      System.out.printf("Here are the checkouts:%n");
      for (Checkout c : checkouts) {
        System.out.printf("%s%n", c);
      }
    }
  }

  // //////////////////////////////////////////////////////////////////////
  // Find checkouts in a directory
  //

  // // Note:  this can be slow, because it examines every directory under your
  // // entire home directory.

  // Find checkouts.  These are indicated by directories named .bzr, CVS,
  // .hg, .git, or .svn.
  //
  // With some version control systems, this task is easy:  there is
  // exactly one .bzr, .hg, or .git directory per checkout.  With CVS and SVN,
  // there is one CVS/.svn directory per directory of the checkout.  It is
  // permitted for one checkout to be made inside another one (though that
  // is bad style), so we must examine every CVS/.svn directory to find all
  // the distinct checkouts.

  // TODO: This should use Files.walkFileTree, which is available since Java 7.

  //   /** Find all checkouts under the given directory. */
  //   static Set<Checkout> findCheckouts(File dir) {
  //     assert dir.isDirectory();
  //
  //     Set<Checkout> checkouts = new LinkedHashSet<>();
  //
  //     findCheckouts(dir, checkouts);
  //
  //     return checkouts;
  //   }

  /**
   * Finds all checkouts at or under the given directory (or, as a special case, also its parent --
   * could rewrite to avoid that case), and adds them to checkouts. Works by checking whether dir or
   * any of its descendants is a version control directory.
   *
   * @param dir the directory under which to search for checkouts
   * @param checkouts the set to populate; is side-effected by this method
   * @param ignoreDirs directories not to search within
   */
  private static void findCheckouts(
      File dir, @Growable Set<Checkout> checkouts, Set<File> ignoreDirs) {
    if (!dir.isDirectory()) {
      // This should never happen, unless the directory is deleted between
      // the call to findCheckouts and the test of isDirectory.
      if (debug) {
        System.out.println("findCheckouts: dir is not a directory: " + dir);
      }
      return;
    }
    if (ignoreDirs.contains(dir)) {
      if (debug) {
        System.out.println("findCheckouts: ignoring " + dir);
      }
      return;
    }

    String dirName = dir.getName();
    File parent = dir.getParentFile();
    if (parent != null) {
      try {
        // The "return" statements below cause the code not to look for
        // checkouts inside version control directories.  (But it does look
        // for checkouts inside other checkouts.)  If someone checks in
        // a .svn file into a Mercurial repository, then removes it, the .svn
        // file remains in the repository even if not in the working copy.
        // That .svn file will cause an exception in dirToCheckoutSvn,
        // because it is not associated with a working copy.
        if (dirName.equals(".bzr")) {
          checkouts.add(new Checkout(RepoType.BZR, parent, null, null));
          return;
        } else if (dirName.equals("CVS")) {
          addCheckoutCvs(dir, parent, checkouts);
          return;
        } else if (dirName.equals(".hg")) {
          checkouts.add(dirToCheckoutHg(dir, parent));
          return;
        } else if (dirName.equals(".git")) {
          checkouts.add(dirToCheckoutGit(dir, parent));
          return;
        } else if (dirName.equals(".svn")) {
          Checkout c = dirToCheckoutSvn(parent);
          if (c != null) {
            checkouts.add(c);
          }
          return;
        }
      } catch (DirectoryDoesNotExist e) {
        throw new Error("This can't happen", e);
      }
    }

    @SuppressWarnings({
      "nullness", // dependent: listFiles => non-null because dir is a directory, and
      // the checker doesn't know that checkouts.add etc do not affect dir
      "allcheckers:purity.functional.argument" // idf prints if there's an IOException.
    })
    File @NonNull [] childdirs = dir.listFiles(idf);
    if (childdirs == null) {
      System.err.printf(
          "childdirs is null (permission or other I/O problem?) for %s%n", dir.toString());
      return;
    }
    Arrays.sort(childdirs, Comparator.comparing(File::getName));
    for (File childdir : childdirs) {
      findCheckouts(childdir, checkouts, ignoreDirs);
    }
  }

  /** Accept only directories that are not symbolic links. */
  static class IsDirectoryFilter implements FileFilter {
    /** Creates a new IsDirectoryFilter. */
    public IsDirectoryFilter() {}

    @Override
    public boolean accept(File pathname) {
      try {
        return pathname.isDirectory() && pathname.getPath().equals(pathname.getCanonicalPath());
      } catch (IOException e) {
        System.err.printf("Exception in IsDirectoryFilter.accept(%s): %s%n", pathname, e);
        throw new UncheckedIOException(e);
        // return false;
      }
    }
  }

  /** An IsDirectoryFilter. */
  static IsDirectoryFilter idf = new IsDirectoryFilter();

  /**
   * Given a directory named {@code CVS}, create a corresponding Checkout object for its parent, and
   * add it to the given set. (Google Web Toolkit does that, for example.)
   *
   * @param cvsDir a {@code CVS} directory
   * @param parentDir its parent
   * @param checkouts the set to populate; is side-effected by this method
   * @throws DirectoryDoesNotExist if the directory does not exist
   */
  static void addCheckoutCvs(File cvsDir, File parentDir, @Growable Set<Checkout> checkouts)
      throws DirectoryDoesNotExist {
    assert cvsDir.getName().equals("CVS") : cvsDir.getName();
    // relative path within repository
    File repositoryFile = new File(cvsDir, "Repository");
    File rootFile = new File(cvsDir, "Root");
    if (!(repositoryFile.exists() && rootFile.exists())) {
      // apparently it wasn't a version control directory
      return;
    }

    String pathInRepo = FilesP.readString(repositoryFile.toPath()).trim();
    @NonNull File repoFileRoot = new File(pathInRepo);
    for (File parent = repoFileRoot.getParentFile();
        parent != null;
        parent = repoFileRoot.getParentFile()) {
      repoFileRoot = parent;
    }

    // strip common suffix off of local dir and repo url
    FilePair stripped =
        removeCommonSuffixDirs(parentDir, new File(pathInRepo), repoFileRoot, "CVS");
    File dirRelative = stripped.file1;
    if (dirRelative == null) {
      System.out.printf("dir (%s) is parent of path in repo (%s)", parentDir, pathInRepo);
      System.exit(1);
    }
    String pathInRepoAtCheckout;
    if (stripped.file2 != null) {
      pathInRepoAtCheckout = stripped.file2.toString();
    } else {
      pathInRepoAtCheckout = dirRelative.getName();
    }

    String repoRoot = FilesP.readString(rootFile.toPath()).trim();
    checkouts.add(new Checkout(RepoType.CVS, dirRelative, repoRoot, pathInRepoAtCheckout));
  }

  /**
   * Given a directory named {@code .hg} , create a corresponding Checkout object for its parent.
   *
   * @param hgDir a {@code .hg} directory
   * @param parentDir its parent
   * @return a Checkout for the {@code .hg} directory
   * @throws DirectoryDoesNotExist if the directory does not exist
   */
  static Checkout dirToCheckoutHg(File hgDir, File parentDir) throws DirectoryDoesNotExist {
    String repository = null;

    File hgrcFile = new File(hgDir, "hgrc");
    Ini ini;
    // There also exist Hg commands that will do this same thing.
    if (hgrcFile.exists()) {
      try (BufferedReader bufferReader = Files.newBufferedReader(hgrcFile.toPath(), UTF_8)) {
        ini = new Ini(bufferReader);
        Profile.Section pathsSection = ini.get("paths");
        if (pathsSection != null) {
          repository = pathsSection.get("default");
          if (repository != null) {
            repository = StringsP.replaceSuffix(repository, "/", "");
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Problem reading file " + hgrcFile, e);
      }
    }

    return new Checkout(RepoType.HG, parentDir, repository, null);
  }

  /**
   * Given a directory named {@code .git}, create a corresponding Checkout object for its parent.
   *
   * @param gitDir a {@code .git} directory
   * @param parentDir its parent
   * @return a Checkout for the {@code .git} directory
   * @throws DirectoryDoesNotExist if the directory does not exist
   */
  static Checkout dirToCheckoutGit(File gitDir, File parentDir) throws DirectoryDoesNotExist {
    // Run `git config` in parentDir, so that it reports the URL of this clone rather than of
    // whatever repository happens to contain the current working directory.
    String repository = UtilP.backticks(parentDir, "git", "config", "remote.origin.url").trim();
    return new Checkout(RepoType.GIT, parentDir, repository, null);
  }

  /**
   * Given a directory that contains a {@code .svn} subdirectory, create a corresponding Checkout
   * object. Returns null if this is not possible.
   *
   * @param parentDir a directory containing a {@code .svn} subdirectory
   * @return a SVN checkout for the directory, or null
   * @throws DirectoryDoesNotExist if the directory does not exist
   */
  static @Nullable Checkout dirToCheckoutSvn(File parentDir) throws DirectoryDoesNotExist {

    // For SVN, do
    //   svn info
    // and grep out these lines:
    // URL: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/myProj/repository/trunk/www
    // Repository Root: svn+ssh://login.csail.mit.edu/afs/csail/group/pag/projects/myProj/repository

    // Use SVNKit?
    // Con: introduces dependency on external library.
    // Pro: no need to re-implement or to call external process (which
    //   might be slow for large checkouts).

    @SuppressWarnings("nullness") // unannotated library: SVNKit
    SVNWCClient wcClient = new SVNWCClient((@Nullable ISVNAuthenticationManager) null, null);
    SVNInfo info;
    try {
      info = wcClient.doInfo(new File(parentDir.toString()), SVNRevision.WORKING);
    } catch (SVNException e) {
      // throw new Error("Problem in dirToCheckoutSvn(" + parentDir + "): ", e);
      System.err.println("Problem in dirToCheckoutSvn(" + parentDir + "): " + e.getMessage());
      if (e.getMessage() != null && e.getMessage().contains("This client is too old")) {
        System.err.println("plume-lib needs a newer version of SVNKit.");
      }
      return null;
    }
    // getFile is null when operating on a working copy, as I am
    // String relativeFile = info.getPath(); // relative to repository root; use to determine root
    // getFile is just the (absolute) local file name for local items -- same as "parentDir"
    // File relativeFile = info.getFile();
    SVNURL url = info.getURL();
    // This can be null, but I don't know under what circumstances. (example: parentDir
    // /afs/csail.mit.edu/u/m/mernst/.snapshot/class/6170/2006-spring/3dphysics)
    SVNURL repoRoot = info.getRepositoryRootURL();
    if (repoRoot == null) {
      System.err.println("Problem:  old svn working copy in " + parentDir.toString());
      System.err.println(
          "Check it out again to get a 'Repository Root' entry in the svn info output.");
      System.err.println("  repoUrl = " + url);
      System.exit(2);
    }
    if (debug) {
      System.out.println();
      System.out.println("repoRoot = " + repoRoot);
      System.out.println(" repoUrl = " + url);
      System.out.println("     parentDir = " + parentDir.toString());
    }

    // Strip common suffix off of local dir and repo url.
    FilePair stripped =
        removeCommonSuffixDirs(
            parentDir, new File(url.getPath()), new File(repoRoot.getPath()), ".svn");
    File dirRelative = stripped.file1;
    if (dirRelative == null) {
      System.out.printf("dir (%s) is parent of repository URL (%s)", parentDir, url.getPath());
      System.exit(1);
    }
    if (stripped.file2 == null) {
      System.out.printf("dir (%s) is child of repository URL (%s)", parentDir, url.getPath());
      System.exit(1);
    }
    String pathInRepoAtCheckout = stripped.file2.toString();
    try {
      url = url.setPath(pathInRepoAtCheckout, false);
    } catch (SVNException e) {
      throw new Error(e);
    }

    if (debug) {
      System.out.println("stripped: " + stripped);
      System.out.println("repoRoot = " + repoRoot);
      System.out.println(" repoUrl = " + url);
      System.out.println("    dirRelative = " + dirRelative.toString());
      System.out.flush();
    }

    assert url.toString().startsWith(repoRoot.toString()) : "repoRoot=" + repoRoot + ", url=" + url;
    return new Checkout(RepoType.SVN, dirRelative, url.toString(), null);

    // // Old implementation
    // String module = url.toString().substring(repoRoot.toString().length());
    // if (module.startsWith("/")) {
    //   module = module.substring(1);
    // }
    // if (module.equals("")) {
    //   module = null;
    // }
    // return new Checkout(RepoType.SVN, dirRelative, repoRoot.toString(), module);

  }

  /**
   * A pair of two files.
   *
   * @param file1 the first file
   * @param file2 the second file
   */
  static record FilePair(@Nullable File file1, @Nullable File file2) {}

  /**
   * Strip identical elements off the end of both paths, and then return what is left of each.
   * Returned elements can be null! If p2Limit is non-null, then it should be a parent of p2, and
   * the stripping stops when p2 becomes p2Limit. If p1Contains is non-null, then p1 must contain a
   * subdirectory of that name, and stripping stops when it is reached.
   *
   * @param p1 the first path
   * @param p2 the second path
   * @param p2Limit null, or a parent of p2, which is the minimum suffix to return
   * @param p1Contains null, or a subdirectory of p1
   * @return p1 and p2, relative to their largest common prefix (modulo {@code p2Limit} and {@code
   *     p1Contains})
   */
  static FilePair removeCommonSuffixDirs(File p1, File p2, File p2Limit, String p1Contains) {
    if (debug) {
      System.out.printf("removeCommonSuffixDirs(%s, %s, %s, %s)%n", p1, p2, p2Limit, p1Contains);
    }
    // new names for results, because we will be side-effecting them
    File r1 = p1;
    File r2 = p2;
    while (r1 != null
        && r2 != null
        && (p2Limit == null || !r2.equals(p2Limit))
        && r1.getName().equals(r2.getName())) {
      File r1Parent = r1.getParentFile();
      if (p1Contains != null && !new File(r1Parent, p1Contains).isDirectory()) {
        break;
      }
      r1 = r1Parent;
      r2 = r2.getParentFile();
    }
    if (debug) {
      System.out.printf("removeCommonSuffixDirs => %s %s%n", r1, r2);
      System.out.flush();
    }
    return new FilePair(r1, r2);
  }

  // //////////////////////////////////////////////////////////////////////
  // Process checkouts
  //

  /**
   * Change pb's command by adding the given argument at the end.
   *
   * @param pb the ProcessBuilder to modify
   * @param arg the argument to add to {@code pb}'s command
   */
  private void addArg(ProcessBuilder pb, String arg) {
    pb.command().add(arg);
  }

  /**
   * Change pb's command by adding the given arguments at the end.
   *
   * @param pb the ProcessBuilder to modify
   * @param args the arguments to add to {@code pb}'s command
   */
  private void addArgs(ProcessBuilder pb, List<String> args) {
    pb.command().addAll(args);
  }

  /**
   * A Replacer does string substitution, to make output more user-friendly. Examples are
   * suppressing noisy output or expanding relative file names.
   */
  static class Replacer {
    /** The regular expression matching text that should be replaced. */
    Pattern regexp;

    /** The replacement text. */
    String replacement;

    /**
     * Create a new Replacer.
     *
     * @param regexp the regular expression matching text that should be replaced
     * @param replacement the replacement text
     */
    public Replacer(@Regex String regexp, String replacement) {
      this.regexp = Pattern.compile(regexp);
      this.replacement = replacement;
    }

    /**
     * Perform replacements on the given string. This method is less prone to StackOverflowError
     * than the JDK's {@code String.replaceAll}.
     *
     * @param s the string in which to perform replacements
     * @return the string, after replacements have been performed
     */
    public String replaceAll(String s) {
      Matcher matcher = regexp.matcher(s);
      return matcher.replaceAll(replacement);
    }
  }

  /**
   * Run the action described by field {@code action}, for each of the clones in {@code checkouts}.
   *
   * @param checkouts the clones and checkouts to process
   */
  public void process(Set<Checkout> checkouts) {
    // Always run at least one command, but sometimes up to three.
    ProcessBuilder pb = new ProcessBuilder("");
    pb.redirectErrorStream(true);
    ProcessBuilder pb2 = new ProcessBuilder(new ArrayList<>());
    pb2.redirectErrorStream(true);
    ProcessBuilder pb3 = new ProcessBuilder(new ArrayList<>());
    pb3.redirectErrorStream(true);
    // pb4 is only for checking whether there are no commits in this branch.
    ProcessBuilder pb4 = new ProcessBuilder(new ArrayList<>());
    pb4.redirectErrorStream(true);
    // pb5 is only for printing the origin URL.
    ProcessBuilder pb5 =
        new ProcessBuilder(List.of(gitExecutable, "config", "--get", "remote.origin.url"));
    pb5.redirectErrorStream(true);

    // I really want to be able to redirect output to a Reader, but that
    // isn't possible.  I have to send it to a file.
    // I can't just use the InputStream directly, because if the process is
    // killed because of a timeout, the stream is inaccessible.

    CLONELOOP:
    for (Checkout c : checkouts) {
      if (debug) {
        System.out.println(c);
        System.out.flush();
      }
      File dir = c.directory;

      List<Replacer> replacers = new ArrayList<>();
      List<Replacer> replacers3 = new ArrayList<>();

      switch (c.repoType) {
        case BZR -> {}
        case CVS -> {
          replacers.add(new Replacer("(^|\\n)([?]) ", "$1$2 " + dir + "/"));
        }
        case GIT -> {
          replacers.add(new Replacer("(^|\\n)fatal:", "$1fatal in " + dir + ":"));
          replacers.add(new Replacer("(^|\\n)warning:", "$1warning in " + dir + ":"));
          replacers.add(
              new Replacer(
                  "(^|\\n)(There is no tracking information for the current branch\\.)",
                  "$1" + dir + ": $2"));
          replacers.add(
              new Replacer("(^|\\n)(Your configuration specifies to merge)", dir + ": $1$2"));
        }
        case HG -> {
          // "real URL" is for bitbucket.org.  (Should be early in list.)
          replacers.add(new Replacer("(^|\\n)real URL is .*\\n", "$1"));
          replacers.add(new Replacer("(^|\\n)(abort: .*)", "$1$2: " + dir));
          replacers.add(new Replacer("(^|\\n)([MARC!?I]) ", "$1$2 " + dir + "/"));
          replacers.add(
              new Replacer(
                  "(^|\\n)(\\*\\*\\* failed to import extension .*: No module named demandload\\n)",
                  "$1"));
          // Hack, should be replaced when googlecode certificate problems are fixed.
          replacers.add(
              new Replacer(
                  "(^|\\n)warning: .* certificate not verified"
                      + " \\(check web.cacerts config setting\\)\\n",
                  "$1"));
          // May appear twice in output with overlapping matches, so repeat the replacer
          replacers.add(
              new Replacer(
                  "(^|\\n)warning: .* certificate not verified"
                      + " \\(check web.cacerts config setting\\)\\n",
                  "$1"));
          // Does this mask too many errors?
          replacers.add(
              new Replacer(
                  "(^|\\n)((comparing with default-push\\n)?"
                      + "abort: repository default(-push)? not found!: .*\\n)",
                  "$1"));
        }
        case SVN -> {
          replacers.add(
              new Replacer("(svn: Network connection closed unexpectedly)", "$1 for " + dir));
          replacers.add(new Replacer("(svn: Repository) (UUID)", "$1 " + dir + " $2"));
          replacers.add(
              new Replacer(
                  "(svn: E155037: Previous operation has not finished; run 'cleanup' if it was"
                      + " interrupted)",
                  "$1; for " + dir));
        }
        default -> {
          assert false;
        }
      }
      // The \r* is necessary here; (sometimes?) there are two carriage returns.
      replacers.add(
          new Replacer(
              "(remote: )?Warning: untrusted X11 forwarding setup failed: xauth key data not"
                  + " generated\r*\n"
                  + "(remote: )?Warning: No xauth data; using fake authentication data for X11"
                  + " forwarding\\.\r*\n",
              ""));
      replacers.add(new Replacer("(working copy ')", "$1" + dir));

      pb.command("echo", "command", "not", "set");
      pb.directory(dir);
      pb2.command(new ArrayList<>());
      pb2.directory(dir);
      pb3.command(new ArrayList<>());
      pb3.directory(dir);
      pb4.command(new ArrayList<>());
      pb4.directory(dir);
      boolean showNormalOutput = false;
      // If true, report whether a local git branch has changes that are on no remote.
      boolean checkUnpushedGitChanges = false;
      // If true, fetch the pull-request heads of each GitHub remote of a git clone.
      boolean fetchGitPullRequestHeads = false;
      // Set pb.command() to be the command to be executed.
      switch (action) {
        case LIST -> {
          System.out.println(c);
          continue CLONELOOP;
        }
        case CLONE -> {
          pb.directory(dir.getParentFile());
          String dirbase = dir.getName();
          if (c.repository == null) {
            System.out.printf("Skipping checkout with unknown repository:%n  %s%n", dir);
            continue CLONELOOP;
          }
          switch (c.repoType) {
            case BZR -> {
              System.out.println("bzr handling not yet implemented: skipping " + c.directory);
            }
            case CVS -> {
              assert c.module != null : "@AssumeAssertion(nullness): dependent type CVS";
              pb.command(
                  cvsExecutable,
                  "-d",
                  c.repository,
                  "checkout",
                  "-P", // prune empty directories
                  "-ko", // no keyword substitution
                  c.module);
              addArgs(pb, cvsArg);
            }
            case GIT -> {
              // "--" is to prevent the directory name from being interpreted as a command-line
              // option, if it starts with a hyphen.
              // "--filter=blob:none" makes cloning fast and reduces disk space.  It makes a
              // subsequent `git blame` command slower, since it has retrieve information from the
              // remote repository.  It makes pulling from the cloned repository impossible.
              pb.command(gitExecutable, "clone", /* "--recursive", */ "--", c.repository, dirbase);
              addArgs(pb, gitArg);
            }
            case HG -> {
              pb.command(hgExecutable, "clone", c.repository, dirbase);
              addArgs(pb, hgArg);
              if (insecure) {
                addArg(pb, "--insecure");
              }
            }
            case SVN -> {
              if (c.module != null) {
                pb.command(svnExecutable, "checkout", c.repository, c.module);
              } else {
                pb.command(svnExecutable, "checkout", c.repository);
              }
              addArgs(pb, svnArg);
            }
            default -> {
              assert false;
            }
          }
        }
        case STATUS -> {
          // I need a replacer for other version control systems, to add
          // directory names.
          showNormalOutput = true;
          switch (c.repoType) {
            case BZR -> {
              System.out.println("bzr handling not yet implemented: skipping " + c.directory);
            }
            case CVS -> {
              assert c.repository != null;
              pb.command(
                  cvsExecutable,
                  "-q",
                  // Including "-d REPOS" seems to give errors when a
                  // subdirectory is in a different CVS repository.
                  // "-d", c.repository,
                  "diff",
                  "-b", // compress whitespace
                  "--brief", // report only whether files differ, not details
                  "-N"); // report new files
              addArgs(pb, cvsArg);
              //         # For the last perl command, this also works:
              //         #   perl -p -e 'chomp(\$cwd = `pwd`); s/^Index: /\$cwd\\//'";
              //         # but the one we use is briefer and uses the abbreviated directory name.
              //         $filter = "grep -v \"unrecognized keyword 'UseNewInfoFmtStrings'\" | grep
              // \"^Index:\" | perl -p -e 's|^Index: |$dir\\/|'";
              String removeRegexp =
                  ("\n=+"
                      + "\nRCS file: .*" // no trailing ,v for newly-created files
                      + "(\nretrieving revision .*)?" // no output for newly-created files
                      + "\ndiff .*"
                      + "(\nFiles .* and .* differ)?" // no output if only whitespace differences
                  );
              replacers.add(new Replacer(removeRegexp, ""));
              replacers.add(new Replacer("(^|\\n)Index: ", "$1" + dir + "/"));
              replacers.add(
                  new Replacer("(^|\\n)(cvs \\[diff aborted)(\\]:)", "$1$2 in " + dir + "$3"));
              replacers.add(new Replacer("(^|\\n)(Permission denied)", "$1$2 in " + dir));
              replacers.add(
                  new Replacer(
                      "(^|\\n)(cvs diff: )(cannot find revision control)",
                      "$1$2 in " + dir + ": $3"));
              replacers.add(new Replacer("(^|\\n)(cvs diff: cannot find )", "$1$2" + dir));
              replacers.add(new Replacer("(^|\\n)(cvs diff: in directory )", "$1$2" + dir + "/"));
              replacers.add(new Replacer("(^|\\n)(cvs diff: ignoring )", "$1$2" + dir + "/"));
            }
            case GIT -> {
              pb.command(gitExecutable, "status");
              addArgs(pb, gitArg);
              // Why was I using this option??
              // addArg(pb, "--untracked-files=no");
              addArg(pb, "--porcelain"); // experimenting with porcelain output
              replacers.add(
                  new Replacer(
                      "(^|\\n)On branch master\\n"
                          + "Your branch is up-to-date with 'origin/master'.\\n"
                          + "\\n?",
                      "$1"));
              replacers.add(
                  new Replacer("(^|\\n)nothing to commit,? working directory clean\\n", "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n"
                          + ")no changes added to commit \\(use \"git add\" and/or \"git commit"
                          + " -a\"\\)\\n",
                      "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)nothing added to commit but untracked files present"
                          + " \\(use \"git add\" to track\\)\\n",
                      "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)nothing to commit \\(use -u to show untracked files\\)\n", "$1"));

              replacers.add(new Replacer("(^|\\n)#\\n", "$1"));
              replacers.add(new Replacer("(^|\\n)# On branch master\\n", "$1"));
              replacers.add(
                  new Replacer("(^|\\n)nothing to commit \\(working directory clean\\)\\n", "$1"));
              replacers.add(new Replacer("(^|\\n)# Changed but not updated:\\n", "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)#   \\(use \"git add <file>...\""
                          + " to update what will be committed\\)\\n",
                      "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)#   \\(use \"git checkout -- <file>...\""
                          + " to discard changes in working directory\\)\\n",
                      "$1"));
              replacers.add(new Replacer("(^|\\n)# Untracked files:\\n", "$1"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)#   \\(use \"git add <file>...\""
                          + " to include in what will be committed\\)\\n",
                      "$1"));

              replacers.add(new Replacer("(^|\\n)(#\tmodified:   )", "$1" + dir + "/"));
              // This must come after the above, since it matches a prefix of the above
              replacers.add(new Replacer("(^|\\n)(#\t)", "$1untracked: " + dir + "/"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)# Your branch is ahead of .*\\n",
                      "$1unpushed changesets: " + dir + "\n"));
              replacers.add(new Replacer("(^|\\n)([?][?]) ", "$1$2 " + dir + "/"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)([ACDMRU][ ACDMRTU]|[ ACDMRU][ACDMRTU]) ", "$1$2 " + dir + "/"));

              // Useful info, but don't bother to report it, for consistency with other VCSes
              replacers.add(
                  new Replacer(
                      "(^|\\n)# Your branch is behind .*\\n",
                      "$1unpushed changesets: " + dir + "\n"));

              // Could remove all other output, but this could suppress messages
              // replacers.add(new Replacer("(^|\\n)#.*\\n", "$1"));

              // Necessary because "git status --porcelain" does not report:
              //   # Your branch is ahead of 'origin/master' by 1 commit.
              checkUnpushedGitChanges = true;

              // TODO: use pb3 to look for stashes, using `git stash list`.

              // TODO: use `if git merge-base --is-ancestor origin/master HEAD ; then ...` to
              // determine whether this branch has no changes and thus can be deleted.
              pb4.command(gitExecutable, "merge-base", "--is-ancestor", "origin/master", "HEAD");
            }
            case HG -> {
              pb.command(hgExecutable, "status");
              addArgs(pb, hgArg);
              if (debug) {
                System.out.printf(
                    "invalidCertificate(%s) => %s%n", c.directory, invalidCertificate(c.directory));
              }
              if (invalidCertificate(c.directory)) {
                pb2.command(hgExecutable, "outgoing", "-l", "1", "--config", "web.cacerts=");
              } else {
                pb2.command(hgExecutable, "outgoing", "-l", "1");
              }
              addArgs(pb2, hgArg);
              if (insecure) {
                addArg(pb2, "--insecure");
              }
              // The third line is either "no changes found" or "changeset".
              replacers.add(
                  new Replacer(
                      "^comparing with .*\\nsearching for changes\\nchangeset[^\001]*",
                      "unpushed changesets: " + dir + "\n"));
              replacers.add(
                  new Replacer(
                      "^\\n?comparing with .*\\nsearching for changes\\nno changes found\n", ""));
              pb3.command(hgExecutable, "shelve", "-l");
              addArgs(pb3, hgArg);
              // Shelve is an optional extension, so don't print anything if not installed.
              replacers3.add(new Replacer("^hg: unknown command 'shelve'\\n(.*\\n)+", ""));
              replacers3.add(new Replacer("^(.*\\n)+", "shelved changes: " + dir + "\n"));
            }
            case SVN -> {
              // Handle some changes.
              // "svn status" outputs an eighth column, if you pass the --show-updates switch: [* ]
              replacers.add(
                  new Replacer("(^|\\n)([ACDIMRX?!~ ][CM ][L ][+ ][$ ]) *", "$1$2 " + dir + "/"));
              pb.command(svnExecutable, "status");
              addArgs(pb, svnArg);
            }
            default -> {
              assert false;
            }
          }
        }
        case PULL -> {
          switch (c.repoType) {
            case BZR -> {
              System.out.println("bzr handling not yet implemented: skipping " + c.directory);
            }
            case CVS -> {
              replacers.add(
                  new Replacer(
                      "(^|\\n)(cvs update: ((in|skipping) directory|conflicts found in )) +",
                      "$1$2 " + dir + "/"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)(Merging differences between 1.16 and 1.17 into )",
                      "$1$2 " + dir + "/"));
              assert c.repository != null;
              pb.command(
                  cvsExecutable,
                  // Including -d causes problems with CVS repositories
                  // that are embedded inside other repositories.
                  // "-d", c.repository,
                  "-Q",
                  "update",
                  "-d");
              addArgs(pb, cvsArg);
              //         $filter = "grep -v \"config: unrecognized keyword
              // 'UseNewInfoFmtStrings'\"";
              replacers.add(new Replacer("(cvs update: move away )", "$1" + dir + "/"));
              replacers.add(new Replacer("(cvs \\[update aborted)(\\])", "$1 in " + dir + "$2"));
            }
            case GIT -> {
              replacers.add(new Replacer("(^|\\n)Already up-to-date\\.\\n", "$1"));
              replacers.add(new Replacer("(^|\\n)error:", "$1error in " + dir + ":"));
              replacers.add(
                  new Replacer(
                      "(^|\\n)Please, commit your changes or stash them before you can merge.\\n"
                          + "Aborting\\n",
                      "$1"));
              replacers.add(
                  new Replacer(
                      "((^|\\n)CONFLICT \\(content\\): Merge conflict in )", "$1" + dir + "/"));
              replacers.add(new Replacer("(^|\\n)([ACDMRU]\t)", "$1$2" + dir + "/"));
              pb.command(gitExecutable, "pull", "-q" /*, "--recurse-submodules"*/);
              addArgs(pb, gitArg);
              // prune branches; alternately do "git remote prune origin"; "git gc" doesn't do this.
              pb2.command(gitExecutable, "fetch", "-p");
              fetchGitPullRequestHeads = true;
            }
            case HG -> {
              replacers.add(new Replacer("(^|\\n)([?!AMR] ) +", "$1$2 " + dir + "/"));
              replacers.add(new Replacer("(^|\\n)abort: ", "$1"));
              pb.command(hgExecutable, "-q", "update");
              addArgs(pb, hgArg);
              if (invalidCertificate(c.directory)) {
                pb2.command(hgExecutable, "-q", "fetch", "--config", "web.cacerts=");
              } else {
                pb2.command(hgExecutable, "-q", "fetch");
              }
              addArgs(pb2, hgArg);
              if (insecure) {
                addArg(pb2, "--insecure");
              }
            }
            case SVN -> {
              replacers.add(new Replacer("(^|\\n)([?!AMR] ) +", "$1$2 " + dir + "/"));
              replacers.add(new Replacer("(svn: Failed to add file ')(.*')", "$1" + dir + "/$2"));
              assert c.repository != null;
              pb.command(svnExecutable, "-q", "update");
              addArgs(pb, svnArg);
              //         $filter = "grep -v \"Killed by signal 15.\"";
            }
            default -> {
              assert false;
            }
          }
        }
        default -> {
          assert false;
        }
      }

      // Check that the directory exists (OK if it doesn't for checkout).
      if (debug) {
        System.out.println(dir + ":");
      }
      if (dir.exists()) {
        if (action == Action.CLONE && !redoExisting) {
          if (!quiet) {
            System.out.println("Skipping checkout (dir already exists): " + dir);
          }
          continue;
        }
      } else {
        // Directory does not exist
        File parent = dir.getParentFile();
        if (parent == null) {
          // This happens when dir is the root directory.
          // It doesn't happen merely when the parent doesn't yet exist.
          System.err.printf("Directory %s does not exist, and it has no parent%n", dir);
          continue;
        }
        switch (action) {
          case CLONE -> {
            if (!parent.exists()) {
              if (show) {
                if (!dryRun) {
                  System.out.printf("Parent directory %s does not exist (creating)%n", parent);
                } else {
                  System.out.printf("  mkdir -p %s%n", parent);
                }
              }
              if (!dryRun) {
                if (!parent.mkdirs()) {
                  System.err.println("Could not create directory: " + parent);
                  System.exit(1);
                }
              }
            }
          }
          case STATUS, PULL -> {
            if (!quiet) {
              System.out.println("Cannot find directory: " + dir);
            }
            continue CLONELOOP;
          }
          case LIST -> {
            assert false;
          }
          default -> {
            assert false;
          }
        }
      }

      if (printDirectory) {
        System.out.println(dir + " :");
        pb5.directory(dir);
        performCommand(pb5, Collections.emptyList(), true);
      }
      performCommand(pb, replacers, showNormalOutput);
      if (!pb2.command().isEmpty()) {
        performCommand(pb2, replacers, showNormalOutput);
      }
      if (!pb3.command().isEmpty()) {
        performCommand(pb3, replacers3, showNormalOutput);
      }
      if (fetchGitPullRequestHeads) {
        fetchGitPullRequestHeads(dir);
      }
      if (checkUnpushedGitChanges && hasUnpushedGitChanges(dir)) {
        System.out.println("unpushed commits: " + dir);
      }
      // TODO:
      // if (!pb4.command().isEmpty()) {
      //   int isAncestorStatus = performCommand(pb4, replacers4, showNormalOutput);
      //   if (isAncestorStatus == 0) {
      //     // TODO: Output this message only for non-master branches.
      //     // System.out.println("No changes committed in " + dir);
      //   }
      // }
      System.out.flush();
    }
  }

  /** Regex for matching the default path for a Mercurial clone. */
  private @Regex(1) Pattern defaultPattern = Pattern.compile("^default[ \t]*=[ \t]*(.*)");

  /**
   * Given a directory containing a Mercurial checkout/clone, return its default path. Return null
   * otherwise.
   *
   * @param dir a directory containing a Mercurial clone
   * @return the path for the Mercurial clone
   */
  // This implementation is not quite right because we didn't look for the
  // [path] section.  We could fix this by using a real ini reader or
  // calling "hg showconfig".  This hack is good enough for now.
  private @Nullable String defaultPath(File dir) {
    File hgrc = new File(new File(dir, ".hg"), "hgrc");
    CommentFormat commentFormat = new CommentFormat("^#.*");
    try (EntryReader er = new EntryReader(hgrc, EntryFormat.DEFAULT, commentFormat, null)) {
      for (String line : er) {
        Matcher m = defaultPattern.matcher(line);
        if (m.matches()) {
          return m.group(1);
        }
      }
    } catch (IOException e) {
      // System.out.printf("IOException: " + e);
      return null;
    }
    return null;
  }

  /** A regular expression that matches a message about invalid certificates. */
  private Pattern invalidCertificatePattern =
      Pattern.compile("^https://[^.]*[.][^.]*[.]googlecode[.]com/hg$");

  /**
   * Returns true if there is an invalid certificate for the given directory.
   *
   * @param dir the directory to test
   * @return true if there is an invalid certificate for the given directory
   */
  private boolean invalidCertificate(File dir) {
    String defaultPath = defaultPath(dir);
    if (debug) {
      System.out.printf("defaultPath=%s for %s%n", defaultPath, dir);
    }
    if (defaultPath == null) {
      return false;
    }
    return (defaultPath.startsWith("https://hg.codespot.com/")
        || invalidCertificatePattern.matcher(defaultPath).matches());
  }

  /**
   * Matches a line of output of {@code git ls-remote} for a pull-request head. Group 1 is the head
   * commit, and group 2 is the pull request's number.
   */
  private static final @Regex(2) Pattern LS_REMOTE_PULL_REQUEST_HEAD =
      Pattern.compile("^(\\S+)\trefs/pull/([^/]+)/head$");

  /** Matches the name of a remote's URL in git's configuration. Group 1 is the remote's name. */
  private static final @Regex(1) Pattern REMOTE_URL_KEY = Pattern.compile("^remote\\.(.*)\\.url$");

  /**
   * Fetches the pull-request heads of each GitHub remote of a git clone, if some local branch has
   * commits that are on no remote. See {@link #fetchGitPullRequestHeads(File, String, Map, boolean,
   * StringBuilder)}. Deletes each ref {@code refs/pull/REMOTE/N} whose remote is not a GitHub
   * remote of the clone, as after the remote has been removed or renamed.
   *
   * @param dir a directory containing a git clone
   */
  private void fetchGitPullRequestHeads(File dir) {
    // "git config", "git for-each-ref", and "git rev-list" only read the clone, so they run even in
    // a dry run, which then shows the commands that use each remote.
    String config =
        gitOutput(
            gitCommand(dir, "config", "--get-regexp", "^(remote\\..*\\.url|core\\.sshcommand)$"),
            null,
            true);
    if (config == null) {
      return;
    }
    Set<String> gitHubRemotes = new LinkedHashSet<>();
    boolean sshCommandConfigured = false;
    for (String line : config.lines().toList()) {
      int space = line.indexOf(' ');
      if (space == -1) {
        continue;
      }
      String key = line.substring(0, space);
      Matcher m = REMOTE_URL_KEY.matcher(key);
      if (key.equals("core.sshcommand")) {
        sshCommandConfigured = true;
      } else if (m.matches() && line.substring(space + 1).contains("github.com")) {
        @SuppressWarnings("nullness:assignment") // group 1 is not optional, so it is non-null
        @NonNull String remote = m.group(1);
        gitHubRemotes.add(remote);
      }
    }

    String localHeadsOutput =
        gitOutput(
            gitCommand(dir, "for-each-ref", "--format=%(objectname) %(refname)", "refs/pull/"),
            null,
            true);
    if (localHeadsOutput == null) {
      return;
    }
    // Maps each GitHub remote to a map from a pull request's number to its fetched head commit.
    Map<String, @Modifiable Map<String, String>> localHeads = new HashMap<>();
    for (String remote : gitHubRemotes) {
      localHeads.put(remote, new HashMap<>());
    }
    StringBuilder deletions = new StringBuilder();
    for (String line : localHeadsOutput.lines().toList()) {
      int space = line.indexOf(' ');
      if (space == -1) {
        continue;
      }
      String ref = line.substring(space + 1);
      // A ref such as refs/pull/R/S/1 belongs to a remote named "R/S".
      int slash = ref.lastIndexOf('/');
      if (slash < "refs/pull/".length()) {
        continue;
      }
      String remote = ref.substring("refs/pull/".length(), slash);
      Map<String, String> remoteLocalHeads = localHeads.get(remote);
      if (remoteLocalHeads == null) {
        deletions.append("delete ").append(ref).append('\n');
      } else {
        remoteLocalHeads.put(ref.substring(slash + 1), line.substring(0, space));
      }
    }

    // Pull-request heads matter only to a local branch whose commits are on no remote.
    String unpushed =
        gitOutput(
            gitCommand(dir, "rev-list", "-n", "1", "--branches", "--not", "--remotes"), null, true);
    if (unpushed != null && !unpushed.isEmpty()) {
      for (String remote : gitHubRemotes) {
        @SuppressWarnings("nullness:assignment") // localHeads has a key for each GitHub remote
        @NonNull Map<String, String> remoteLocalHeads = localHeads.get(remote);
        fetchGitPullRequestHeads(dir, remote, remoteLocalHeads, sshCommandConfigured, deletions);
      }
    }

    if (deletions.length() != 0) {
      gitOutput(gitCommand(dir, "update-ref", "--stdin"), deletions.toString(), false);
    }
  }

  /**
   * Fetches the pull-request heads of a GitHub remote of a git clone. The head of pull request N,
   * which is ref {@code refs/pull/N/head} of the remote, is fetched into ref {@code
   * refs/pull/REMOTE/N}. Only the pull requests whose head commit the clone has and is on no remote
   * branch, or whose head the clone fetched before, are fetched; a remote might have thousands of
   * other pull requests, most of them landed. A ref is to be deleted when the remote no longer has
   * its pull request.
   *
   * @param dir a directory containing a git clone
   * @param remote a GitHub remote of the clone
   * @param localHeads maps the number of each pull request of the remote that the clone fetched
   *     before to its fetched head commit
   * @param sshCommandConfigured true if git's configuration sets {@code core.sshCommand}
   * @param deletions to which to append an instruction for {@code git update-ref --stdin} that
   *     deletes each ref whose pull request the remote no longer has
   */
  private void fetchGitPullRequestHeads(
      File dir,
      String remote,
      Map<String, String> localHeads,
      boolean sshCommandConfigured,
      StringBuilder deletions) {
    ProcessBuilder lsRemote = gitCommand(dir, "ls-remote", remote, "refs/pull/*/head");
    disableGitPrompts(lsRemote, sshCommandConfigured);
    String remoteHeadsOutput = gitOutput(lsRemote, null, false);
    if (remoteHeadsOutput == null) {
      return;
    }

    // Maps a pull request's number to its head commit.
    Map<String, String> remoteHeads = new LinkedHashMap<>();
    for (String line : remoteHeadsOutput.lines().toList()) {
      Matcher m = LS_REMOTE_PULL_REQUEST_HEAD.matcher(line);
      if (m.matches()) {
        @SuppressWarnings("nullness:assignment") // group 1 is not optional, so it is non-null
        @NonNull String commit = m.group(1);
        @SuppressWarnings("nullness:assignment") // group 2 is not optional, so it is non-null
        @NonNull String number = m.group(2);
        remoteHeads.put(number, commit);
      }
    }

    Set<String> unpushedHeads = new HashSet<>();
    if (!remoteHeads.isEmpty()) {
      // "--ignore-missing" ignores a head commit that the clone lacks.
      String unpushedOutput =
          gitOutput(
              gitCommand(dir, "rev-list", "--ignore-missing", "--stdin", "--not", "--remotes"),
              String.join("\n", remoteHeads.values()) + "\n",
              false);
      if (unpushedOutput == null) {
        return;
      }
      unpushedHeads.addAll(unpushedOutput.lines().toList());
    }

    String refPrefix = "refs/pull/" + remote + "/";
    List<String> refspecs = new ArrayList<>();
    for (Map.Entry<String, String> entry : remoteHeads.entrySet()) {
      String number = entry.getKey();
      String commit = entry.getValue();
      String localCommit = localHeads.get(number);
      if (!commit.equals(localCommit) && (localCommit != null || unpushedHeads.contains(commit))) {
        refspecs.add("+refs/pull/" + number + "/head:" + refPrefix + number + "\n");
      }
    }
    if (!refspecs.isEmpty()) {
      ProcessBuilder fetch =
          gitCommand(dir, "fetch", "-q", "--no-write-fetch-head", "--stdin", remote);
      disableGitPrompts(fetch, sshCommandConfigured);
      gitOutput(fetch, String.join("", refspecs), false);
    }

    for (String number : localHeads.keySet()) {
      if (!remoteHeads.containsKey(number)) {
        deletions.append("delete ").append(refPrefix).append(number).append('\n');
      }
    }
  }

  /**
   * Makes a git command that contacts a remote fail rather than prompt for credentials, as for a
   * remote whose repository has been deleted. Does not change the ssh command if the user has
   * customized it.
   *
   * @param pb a command made by {@link #gitCommand}
   * @param sshCommandConfigured true if git's configuration sets {@code core.sshCommand}
   */
  private void disableGitPrompts(ProcessBuilder pb, boolean sshCommandConfigured) {
    Map<String, String> env = pb.environment();
    env.put("GIT_TERMINAL_PROMPT", "0");
    // An empty GIT_ASKPASS overrides core.askPass and SSH_ASKPASS, so git runs no askpass helper.
    env.put("GIT_ASKPASS", "");
    if (!sshCommandConfigured
        && !env.containsKey("GIT_SSH_COMMAND")
        && !env.containsKey("GIT_SSH")) {
      env.put("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
    }
  }

  /**
   * Returns true if some local branch of a git clone has changes that are on no remote. A branch's
   * changes are on a remote if its commits are reachable from a remote branch, or if its changes,
   * or those of a fetched pull-request head that contains its commits, are in a remote's main
   * branch (see {@link #isInGitMainBranch}). If git fails, reports the failure and returns true.
   *
   * @param dir a directory containing a git clone
   * @return true if some local branch has changes that are on no remote, or if git fails
   */
  private boolean hasUnpushedGitChanges(File dir) {
    List<String> revList = new ArrayList<>(List.of("rev-list", "--branches", "--not", "--remotes"));
    revList.addAll(gitArg);
    // If git fails, then gitOutput reports the problem.
    String unpushedOutput = gitOutput(dir, revList.toArray(new String[0]));
    if (unpushedOutput == null) {
      return true;
    }
    if (unpushedOutput.isEmpty()) {
      return false;
    }
    Set<String> unpushedCommits = new HashSet<>(unpushedOutput.lines().toList());
    String remotes = gitOutput(dir, "remote");
    String branchHeads =
        gitOutput(dir, "for-each-ref", "--format=%(objectname) %(authordate:unix)", "refs/heads");
    if (remotes == null || branchHeads == null) {
      return true;
    }
    Set<String> mainBranchCommits = new LinkedHashSet<>();
    if (!remotes.isEmpty()) {
      // A pattern such as refs/remotes/*/master would not match a remote whose name contains "/".
      List<String> mainBranchesCommand = new ArrayList<>();
      mainBranchesCommand.add("for-each-ref");
      mainBranchesCommand.add("--format=%(objectname)");
      for (String remote : remotes.lines().toList()) {
        for (String branch : List.of("HEAD", "master", "main")) {
          mainBranchesCommand.add("refs/remotes/" + remote + "/" + branch);
        }
      }
      String mainBranchHeads = gitOutput(dir, mainBranchesCommand.toArray(new String[0]));
      if (mainBranchHeads == null) {
        return true;
      }
      mainBranchCommits.addAll(mainBranchHeads.lines().toList());
    }
    for (Map.Entry<String, Long> branchHead : authorDates(branchHeads).entrySet()) {
      String commit = branchHead.getKey();
      if (!unpushedCommits.contains(commit)) {
        continue;
      }
      // A pull request might have been squash-merged along with later commits that changed the
      // same lines as the branch, and then the pull request's head is in the main branch but the
      // branch is not.
      String pullRequestHeads =
          gitOutput(
              dir,
              "for-each-ref",
              "--contains",
              commit,
              "--format=%(objectname) %(authordate:unix)",
              "refs/pull");
      if (pullRequestHeads == null) {
        return true;
      }
      Map<String, Long> commits = new LinkedHashMap<>();
      commits.put(commit, branchHead.getValue());
      commits.putAll(authorDates(pullRequestHeads));
      if (!isInGitMainBranch(dir, commits, mainBranchCommits)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parses output of {@code git for-each-ref --format='%(objectname) %(authordate:unix)'}.
   *
   * @param forEachRefOutput the output of {@code git for-each-ref}
   * @return a map from each commit in the output to its author date, in seconds since the epoch
   */
  private static Map<String, Long> authorDates(String forEachRefOutput) {
    Map<String, Long> result = new LinkedHashMap<>();
    for (String line : forEachRefOutput.lines().toList()) {
      int space = line.indexOf(' ');
      if (space != -1) {
        result.put(line.substring(0, space), Long.parseLong(line.substring(space + 1)));
      }
    }
    return result;
  }

  /**
   * The maximum number of commits of a main branch that {@link #isInGitMainBranch} examines for
   * each given commit.
   */
  private static final int MAX_MAIN_BRANCH_CANDIDATES = 50;

  /**
   * Returns true if the changes of some of the given commits are in some commit of a remote's main
   * branch, as happens when a commit has been squash-merged into the main branch. That is, merging
   * the given commit into that main-branch commit yields the main-branch commit's own tree.
   *
   * <p>For each main-branch head, the main-branch commits examined are the oldest {@link
   * #MAX_MAIN_BRANCH_CANDIDATES} of its first-parent ancestors that descend from the given commit's
   * merge base with it, that are not ancestors of the given commit, that were committed no earlier
   * than the given commit was authored, and that change some file that the given commit changes,
   * relative to the merge base.
   *
   * <p>Returns true even if the main branch has changed the same lines since then. Returns false
   * for a commit that was squash-merged along with later commits that changed the same lines.
   *
   * @param dir a directory containing a git clone
   * @param commits maps commits of the clone to their author dates, in seconds since the epoch
   * @param mainBranchCommits the heads of the remotes' main branches
   * @return true if the changes of some of the commits are in some commit of a remote's main branch
   */
  private boolean isInGitMainBranch(
      File dir, Map<String, Long> commits, Set<String> mainBranchCommits) {
    // Maps each merge to perform, a main-branch commit and a given commit, to the main-branch
    // commit's tree.
    Map<String, String> merges = new LinkedHashMap<>();
    for (Map.Entry<String, Long> entry : commits.entrySet()) {
      String commit = entry.getKey();
      long authorDate = entry.getValue();
      for (String mainBranchCommit : mainBranchCommits) {
        // "git merge-base" fails without error output if the commits have no merge base.
        String mergeBase = gitOutput(dir, "merge-base", mainBranchCommit, commit);
        if (mergeBase == null) {
          continue;
        }
        mergeBase = mergeBase.strip();
        String changedFiles = gitOutput(dir, "diff", "--name-only", "-z", mergeBase, commit);
        if (changedFiles == null) {
          continue;
        }
        if (changedFiles.isEmpty()) {
          // Merging the commit into the main-branch commit yields the main-branch commit's tree.
          return true;
        }
        String revListInput =
            mainBranchCommit
                + "\n^"
                + commit
                + "\n--\n"
                + String.join("\n", changedFiles.split("\0"))
                + "\n";
        // "git merge-tree --stdin" fails for all the merges if one of them is of commits that have
        // no merge base, as can be a main-branch commit that does not descend from the merge base.
        String candidates =
            gitOutput(
                gitCommand(
                    dir,
                    "--literal-pathspecs",
                    "rev-list",
                    "--first-parent",
                    "--ancestry-path=" + mergeBase,
                    "--no-commit-header",
                    "--format=%H %T %ct",
                    "--stdin"),
                revListInput,
                false);
        if (candidates == null) {
          continue;
        }
        // The merges with the candidates, newest first, each with the candidate's tree.
        List<Map.Entry<String, String>> candidateMerges = new ArrayList<>();
        for (String candidate : candidates.lines().toList()) {
          int treeStart = candidate.indexOf(' ') + 1;
          int dateStart = candidate.lastIndexOf(' ') + 1;
          if (0 < treeStart
              && treeStart < dateStart
              && Long.parseLong(candidate.substring(dateStart)) >= authorDate) {
            candidateMerges.add(
                Map.entry(
                    candidate.substring(0, treeStart) + commit,
                    candidate.substring(treeStart, dateStart - 1)));
          }
        }
        int size = candidateMerges.size();
        for (Map.Entry<String, String> merge :
            candidateMerges.subList(Math.max(0, size - MAX_MAIN_BRANCH_CANDIDATES), size)) {
          merges.put(merge.getKey(), merge.getValue());
        }
      }
    }
    if (merges.isEmpty()) {
      return false;
    }

    String results =
        gitOutput(
            gitCommand(dir, "merge-tree", "--stdin", "--name-only", "--no-messages"),
            String.join("\n", merges.keySet()) + "\n",
            false);
    if (results == null) {
      return false;
    }
    // For each merge, the output is the merge's status (1 if it has no conflicts), its tree, the
    // files that have conflicts, and an empty string, each terminated by NUL.
    String[] fields = results.split("\0", -1);
    int i = 0;
    for (String tree : merges.values()) {
      if (i + 1 >= fields.length) {
        break;
      }
      if (fields[i].equals("1") && fields[i + 1].equals(tree)) {
        return true;
      }
      i += 2;
      while (i < fields.length && !fields[i].isEmpty()) {
        i++;
      }
      i++;
    }
    return false;
  }

  /**
   * Returns a command that runs git in the given directory.
   *
   * @param dir the directory in which to run git
   * @param args the arguments to git
   * @return a command that runs git in the given directory
   */
  private ProcessBuilder gitCommand(File dir, String... args) {
    List<String> command = new ArrayList<>(args.length + 1);
    command.add(gitExecutable);
    command.addAll(Arrays.asList(args));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(dir);
    return pb;
  }

  /**
   * Runs git in the given directory and returns its standard output. See {@link
   * #gitOutput(ProcessBuilder, String, boolean)}.
   *
   * @param dir the directory in which to run git
   * @param args the arguments to git
   * @return git's standard output, or null if git failed or timed out
   */
  private @Nullable String gitOutput(File dir, String... args) {
    return gitOutput(gitCommand(dir, args), null, false);
  }

  /**
   * Runs a git command and returns its standard output. If {@link #show} is set, prints the command
   * first. If git fails, prints the command and git's error output. Some git commands, such as
   * {@code git merge-base}, fail without error output to give a negative answer, and then this
   * prints nothing.
   *
   * @param pb a command made by {@link #gitCommand}
   * @param input git's standard input, or null if it has none
   * @param runInDryRun if false and {@link #dryRun} is set, then do not run git, and return the
   *     empty string
   * @return git's standard output, or null if git failed or timed out
   */
  private @Nullable String gitOutput(
      ProcessBuilder pb, @Nullable String input, boolean runInDryRun) {
    if (show) {
      System.out.println(command(pb));
    }
    if (dryRun && !runInDryRun) {
      return "";
    }
    CommandResult result = runCommand(pb, input, false);
    if (debugProcessOutput) {
      System.out.println("preoutput=<<<" + result.output() + ">>>");
    }
    if (result.exitValue() == 0) {
      return result.output();
    }
    // runCommand has already reported a timeout.
    if (!result.timedOut() && !result.errorOutput().isEmpty()) {
      if (!show) {
        System.out.println(command(pb));
      }
      System.out.print(result.errorOutput());
    }
    System.out.flush();
    return null;
  }

  /**
   * The result of running a command.
   *
   * @param exitValue the command's exit value: 0 for normal completion, non-zero for erroneous
   *     completion
   * @param timedOut true if the command was killed because it ran longer than {@link #timeout}
   * @param output the command's standard output, and also its standard error if they were merged
   * @param errorOutput the command's standard error, or the empty string if it was merged into
   *     {@code output}
   */
  static record CommandResult(int exitValue, boolean timedOut, String output, String errorOutput) {}

  /**
   * Perform {@code pb}'s command.
   *
   * @param pb the ProcessBuilder whose commands to run
   * @param replacers replacements to make in the output before displaying it, to reduce verbosity
   * @param showNormalOutput if true, then display the output even if the process completed
   *     normally. Ordinarily, output is displayed only if the process completed erroneously.
   * @return the status code: 0 for normal completion, non-zero for erroneous completion
   */
  int performCommand(ProcessBuilder pb, List<Replacer> replacers, boolean showNormalOutput) {
    if (show) {
      System.out.println(command(pb));
      System.out.flush();
    }
    if (dryRun) {
      return 0;
    }
    CommandResult result = runCommand(pb, null, true);
    int exitValue = result.exitValue();

    // Under what conditions should the output be printed?
    //  * for status, always
    //  * whenever the process exited non-normally
    //  * when debugging
    //  * other circumstances?
    // I could try printing always, to better understand this question.
    if (showNormalOutput || exitValue != 0 || debugReplacers || debugProcessOutput) {
      // Filter then print the output.
      String output = result.output();

      if (debugReplacers || debugProcessOutput) {
        System.out.println("preoutput=<<<" + output + ">>>");
      }
      if (!output.equals("")) {
        boolean noReplacement = false;
        for (Replacer r : replacers) {
          String printableRegexp = r.regexp.toString().replace("\r", "\\r").replace("\n", "\\n");
          if (debugReplacers) {
            System.out.println("midoutput_pre[" + printableRegexp + "]=<<<" + output + ">>>");
          }
          String origOutput = output;
          // Don't loop, because some regexps will continue to match repeatedly
          try {
            output = r.replaceAll(output);
          } catch (StackOverflowError soe) {
            noReplacement = true;
          } catch (Throwable e) {
            System.out.println("Exception in replaceAll.");
            System.out.println("  defaultDirectory = " + pb.directory());
            System.out.println("  cmdLine = " + StringsP.join(" ", pb.command()));
            System.out.println("  regexp = " + printableRegexp);
            System.out.println("  orig output (size " + origOutput.length() + ") = " + origOutput);
            System.out.println("  output (size " + output.length() + ") = " + output);
            throw e;
          }
          if (debugReplacers) {
            System.out.println("midoutput_post[" + printableRegexp + "]=<<<" + output + ">>>");
          }
        }
        if (debugReplacers || debugProcessOutput) {
          System.out.println("postoutput=<<<" + output + ">>>");
        }
        if (debugReplacers) {
          for (int i = 0; i < Math.min(100, output.length()); i++) {
            System.out.println(
                i + ": " + (int) output.charAt(i) + "\n        \"" + output.charAt(i) + "\"");
          }
        }
        if (noReplacement) {
          System.out.println(
              "No replacement done in " + pb.directory() + " because output is too long.");
        }
        if (output.startsWith("You are not currently on a branch.")) {
          System.out.println(pb.directory() + ":");
        }
        System.out.print(output);
        if (noReplacement) {
          System.out.println("End of output for " + pb.directory() + ".");
        }
      }
    }
    System.out.flush();

    return exitValue;
  }

  /**
   * Runs {@code pb}'s command and waits for it to finish. If the command runs longer than {@link
   * #timeout}, kills it and says so.
   *
   * @param pb the command to run, with its directory and environment
   * @param input the command's standard input, or null if it has none
   * @param mergeErrorOutput if true, the result's output contains both the command's standard
   *     output and its standard error
   * @return the result of running the command
   */
  CommandResult runCommand(ProcessBuilder pb, @Nullable String input, boolean mergeErrorOutput) {
    // For debugging
    //  my $command_cwd_sanitized = $command_cwd;
    //  $command_cwd_sanitized =~ s/\//_/g;
    //  $tmpfile = "/tmp/cmd-output-$$-$command_cwd_sanitized";
    // my $command_redirected = "$command > $tmpfile 2>&1";

    // We have changed from using plume.TimeLimitProcess to using
    // the Apache Commons Exec package.  To simplify the conversion
    // we have kept the ProcessBuilder argument but now only use its
    // members to construct the Commons Exec objects.

    @SuppressWarnings({"value"}) // ProcessBuilder.command() returns a non-empty list
    String @MinLen(1) [] args = (String @MinLen(1) []) pb.command().toArray(new String[0]);
    CommandLine cmdLine = new CommandLine(args[0]); // constructor requires executable name
    String[] argArray = Arrays.copyOfRange(args, 1, args.length);
    // Pass each argument verbatim; commons-exec's quoting would add literal quotation marks.
    cmdLine.addArguments(argArray, false);
    DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
    @SuppressWarnings("nullness") // defaults to non-null and was never reset
    @NonNull File defaultDirectory = pb.directory();
    DefaultExecutor executor =
        DefaultExecutor.builder().setWorkingDirectory(defaultDirectory).get();

    ExecuteWatchdog watchdog =
        ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(timeout)).get();
    executor.setWatchdog(watchdog);

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    final ByteArrayOutputStream errStream =
        mergeErrorOutput ? outStream : new ByteArrayOutputStream();
    // Send the subprocess's standard output and its standard error to their streams only a
    // complete line at a time.  `PumpStreamHandler` pumps each stream in a thread of its own, so
    // writing to `outStream` directly would let one stream's output land in the middle of a line
    // of the other stream's output, when both go to `outStream`.
    //
    // These streams need no closing:  they hold no resource, `outStream` and `errStream` belong to
    // this method, and the code below flushes them.
    @SuppressWarnings("resourceleak:required.method.not.called")
    LineAtomicOutputStream outPump = new LineAtomicOutputStream(outStream);
    @SuppressWarnings("resourceleak:required.method.not.called")
    LineAtomicOutputStream errPump = new LineAtomicOutputStream(errStream);
    PumpStreamHandler streamHandler =
        input == null
            ? new PumpStreamHandler(outPump, errPump)
            : new PumpStreamHandler(
                outPump, errPump, new ByteArrayInputStream(input.getBytes(UTF_8)));
    executor.setStreamHandler(streamHandler);

    try {
      executor.execute(cmdLine, pb.environment(), resultHandler);
    } catch (IOException e) {
      String msg = e.toString();
      if (msg.startsWith("java.io.IOException: Cannot run program \"")
          && msg.endsWith(", No such file or directory")) {
        System.err.println(msg.substring(21));
      } else {
        throw new UncheckedIOException(e);
      }
    }

    int exitValue;
    try {
      resultHandler.waitFor();
      exitValue = resultHandler.getExitValue();
    } catch (InterruptedException e) {
      throw new Error(e);
    }
    // A stream's last line might not end with a line separator.
    try {
      outPump.flush();
      errPump.flush();
    } catch (IOException e) {
      // `outStream` and `errStream` are ByteArrayOutputStreams, which never throw IOException.
      throw new UncheckedIOException(e);
    }
    boolean timedOut = executor.isFailure(exitValue) && watchdog.killedProcess();

    if (timedOut) {
      System.out.printf("Timed out (limit: %ss):%n", timeout);
      System.out.println(command(pb));
      System.out.flush();
    }

    String errorOutput = mergeErrorOutput ? "" : errStream.toString(UTF_8);
    return new CommandResult(exitValue, timedOut, outStream.toString(UTF_8), errorOutput);
  }

  /**
   * Returns the shell command for a process.
   *
   * @param pb the process whose command to return
   * @return the shell command for the process
   */
  String command(ProcessBuilder pb) {
    return "  cd " + pb.directory() + "\n  " + StringsP.join(" ", pb.command());
  }

  /**
   * An output stream that writes to another output stream, a whole line at a time. Two of these
   * that write to the same stream never interleave their output within a line, even when different
   * threads write to them; each line of the result comes from exactly one of them.
   *
   * <p>This is for merging a subprocess's standard output and standard error, which are pumped by
   * two different threads.
   *
   * <p>A client must {@link #flush} this stream when done with it; otherwise a final line that has
   * no line separator would never be written.
   */
  private static class LineAtomicOutputStream extends OutputStream {

    /** Where to write complete lines. */
    private final OutputStream delegate;

    /** What has been written to this, since the most recent line separator. */
    private final ByteArrayOutputStream partialLine = new ByteArrayOutputStream();

    /**
     * Creates a new LineAtomicOutputStream.
     *
     * @param delegate where to write complete lines
     */
    LineAtomicOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(@PolySigned int b) throws IOException {
      partialLine.write(b);
      // `write` writes only the low 8 bits of its argument.
      if ((b & 0xff) == '\n') {
        flush();
      }
    }

    /** Writes what has been buffered, even if it is not a complete line. */
    @Override
    public void flush() throws IOException {
      if (partialLine.size() != 0) {
        // Synchronize on `delegate`, because another LineAtomicOutputStream might be writing to it.
        synchronized (delegate) {
          partialLine.writeTo(delegate);
          delegate.flush();
        }
        partialLine.reset();
      }
    }
  }
}
