git-filter-branch-msgs
==================

Update mentions of hashes in commits messages — for `git-filter-branch`. In Scala.

Goal
----

Many commit messages mention other commits by hash. However, when using `git-filter-branch`, all hashes are changed completely, so such references are broken. `git-filter-branch-msgs` updates these references to point to the rewritten commits.

Request for comments
--------------------

Do you think this could be useful? If so, let me know, open issues when something does not work.
There are a few known limitations, and I'll be happy to work on them (or accept contributions) if there's interest.

Status
------

This is an early beta, written in a day and a half. It worked well for me on two splits in one project, but that's the testing it got.

Moreover, `git-filter-branch` is a dangerous beast anyway, so you're supposed to know what you're doing.

Limitations/bugs
----

* No command-line options for tuning settings.
* Recognizing commit IDs requires heuristics, and they cannot be perfect. Right now, instances of [0-9a-f]* are rewritten if:
   * They're at least 5 chars long (this should be configurable). 
   * They are valid commit IDs, as identified by git itself.
   * git-filter-branch already rewrote them to a commit with the same title.
* No system testing whatsoever. Testing this requires quite some automation and I'm not sure how to best test it.
* There's no "real" deployment yet.
* The tool uses git command-line tools instead of any git binding (in particular, JGit). But to use this tool, you need an installation of command-line git tools, so I am not sure switching to JGit would be an improvement (there might be a performance improvement, but I'm not sure it's high enough).

Installation/usage instruction
------------------------

I think what's below should list all the steps needed, but you should not do them blindly; details and caveats are probably missing.

1. Install [nailgun 0.9.1](http://www.martiansoftware.com/nailgun/). You'll just need the C client, not the Java part.
   Precompiled binaries can be stolen [from zinc](https://github.com/typesafehub/zinc/tree/master/dist/src/dist/bin/ng), in particular [for Windows](https://github.com/typesafehub/zinc/raw/master/dist/src/dist/bin/ng/win32/ng.exe).
2. Allow the client being invoked simply as `git-filter-branch-msgs`:

    ```bash
    $ ln -s $(which ng) ~/bin/git-filter-branch-msgs
    ```
    If you do not do that (for instance because it's inconvenient, or you are on Windows), you'll need to invoke the client as `ng git-filter-branch-msgs` instead.
3. Run `sbt stage` in this directory to compile this program and generate launcher scripts for it. This requires [SBT](http://www.scala-sbt.org/) 0.13 or later; all other dependencies will be automatically downloaded and cached.
4. Run the server with `./target/universal/stage/bin/git-filter-branch-msgs`
5. Now you can use it with `git-filter-branch`.
    The intended usage pattern is through

  ```bash
  git-filter-branch
    --msg-filter 'git-filter-branch-msgs' $your_other_filters_here \
    -- --date-order $other_git_rev_list_params
  ```

    However, it turned out that currently --date-order must be added directly inside `git-filter-branch`. We provide a fixed version of git-filter-branch, incorporating this option directly, in`scripts/git-filter-branch`.

6. After running `git-filter-branch`, you can kill the server with Ctrl-C.
7. The server will create a log, named `echo-translate.log`, where it is running. Check it for anything amiss.

It should be possible in theory to use this program without nailgun, but that will start a JVM for each commit, so it is not recommended for any non-trivial project.
Plus, I don't provide complete docs for it (you need to change `mainClass` in `build.sbt` to `filterbranch.EchoTranslate` and redo `sbt stage`, then you'll need git to use the produced script). I could do it if there's interest.
