filter-branch-msgs
==================

Update mentions of hashes in commits messages — for filter-branch. Implemented in Scala.

Goal
----

Many commit messages mention other commits by hash. However, when using `git-filter-branch`, all hashes are changed completely, so such references are broken. `filter-branch-msgs` can update these references to point to the rewritten commits.

Warning: I wrote this in an afternoon, and it's been tested only lightly. Plus, `git-filter-branch` is a dangerous beast anyway, so you're supposed to know what you're doing.

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
4. Run the server with `./target/universal/stage/bin/filter-branch-msgs`
5. Now you can use it with `git-filter-branch`.
    The intended usage pattern is through

  ```bash
  git-filter-branch
    --date-order \
    --msg-filter 'git-filter-branch-msgs' $your_other_filters_here
  ```

6. After running `git-filter-branch`, you can kill the server with Ctrl-C.
7. The server will create a log, named `echo-translate.log`, where it is running. Check it for anything amiss.

It should be possible in theory to use this program without nailgun, but that will start a JVM for each commit, so it is not recommended for any non-trivial project.
Plus, I don't provide complete docs for it (you need to change `mainClass` in `build.sbt` to `filterbranch.EchoTranslate` and redo `sbt stage`, then you'll need git to use the produced script). I could do it if there's interest.
