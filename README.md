# multi-version-control

Lets you run a version control command, such as `status` or `pull`, on
a set of clones or checkouts.  Supports CVS, Git, Hg, and SVN.

## Use

See the [documentation](http://plumelib.org/multi-version-control/api/org/plumelib/multiversioncontrol/MultiVersionControl.html).

## Installation

```
cd SOME_DIRECTORY
git clone https://github.com/plume-lib/multi-version-control
cd multi-version-control
./gradlew assemble
```

Add to your shell startup file, such as `~/.profile`:
```
alias mvc='java -ea -cp SOME_DIRECTORY/multi-version-control/build/libs/multi-version-control-all.jar org.plumelib.multiversioncontrol.MultiVersionControl'
```

## Other scripts for managing multiple git clones and branches

See the scripts in [manage-git-branches](https://github.com/plume-lib/manage-git-branches).
