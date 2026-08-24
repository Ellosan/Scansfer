# Getting Scansfer onto F-Droid

F-Droid does not accept an APK. It builds the app itself, from this source
repository, and signs the result with its own key. So "submitting" means giving
F-Droid a short description of where the code lives and how to build it.

That description is `com.scansfer.app.yml`, next to this file.

## Before submitting

- [x] The app is licensed (MIT, see `LICENSE` in the project root)
- [x] Every dependency is open source — no Google Play Services, Firebase or ML Kit
- [x] Store text and icon exist in `fastlane/metadata/android/en-US/`
- [x] The release is tagged in git as `v2.1.3`
- [x] Screenshots added to `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- [x] The code is on the repository's default branch
- [ ] The GitHub repository is public

Note that F-Droid reads the store text and screenshots from the tagged
release, not from the default branch, so any change to them needs a new tag
before it will show up on the listing.

## Submitting

The file `com.scansfer.app.yml` next to this one is exactly what F-Droid needs.
Paste it in as-is: no extra comments, and nothing added or removed by hand.

`AutoName` in particular has to stay. `fdroid checkupdates` derives it from the
app's manifest, and fdroiddata's CI runs that tool and then fails the job if it
produced any change at all. So the committed file must already contain exactly
what the tool would write, `AutoName` included, in the position it puts it:
after `IssueTracker`, with a blank line either side. Dropping it fails the
`checkupdates` job even though the file is otherwise valid.

1. Make a free account at https://gitlab.com
2. Open https://gitlab.com/fdroid/fdroiddata and press **Fork**. The fork has to
   be **public**, and the branch you work on must be **unprotected** — fdroiddata
   merges fast-forward and cannot do it otherwise.
3. In your fork, create `metadata/com.scansfer.app.yml` and paste in the
   contents of `com.scansfer.app.yml` from this folder
4. Commit it with the message: `New app: Scansfer`
5. Open a merge request against `fdroid/fdroiddata`, titled `New app: Scansfer`
6. In the merge request's **Description** box, pick the **App inclusion**
   template from the dropdown, delete the instructions at the top, tick the
   boxes that apply, and remove the `Closes rfp#` / `Closes fdroiddata#` lines
   if there is no matching issue

Then wait for the pipeline to finish. Only tick "Builds with `fdroid build` and
all pipelines pass" once it has actually gone green.

F-Droid volunteers then review it. They may ask for changes; that is normal and
is not a rejection.

## After it is published

To release a new version later: raise `versionCode` and `versionName` in
`app/build.gradle.kts`, add a changelog file named after the new `versionCode`
under `fastlane/metadata/android/en-US/changelogs/`, then tag the commit
(`git tag v2.2.0 && git push origin v2.2.0`). F-Droid watches the tags and picks
it up on its own — no second merge request needed.

Note that F-Droid signs with its own key, not the one used for the APKs built
here. Anyone running a sideloaded build has to uninstall it before they can
install the F-Droid one.
