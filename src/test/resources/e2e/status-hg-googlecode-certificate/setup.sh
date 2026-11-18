# shellcheck shell=bash
. "$(dirname "$0")/../common.sh"
make_fake_hg_clone googlecode-subdomain https://wiki.proj.googlecode.com/hg
make_fake_hg_clone googlecode https://proj.googlecode.com/hg
make_fake_hg_clone codespot https://hg.codespot.com/a/proj
make_fake_hg_clone other https://www.mercurial-scm.org/repo/hg
