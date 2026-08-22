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

1. Make a free account at https://gitlab.com
2. Open https://gitlab.com/fdroid/fdroiddata and press **Fork**
3. In your fork, create `metadata/com.scansfer.app.yml` and paste in the
   contents of `com.scansfer.app.yml` from this folder
4. Commit it with the message: `New App: com.scansfer.app`
5. Open a merge request against `fdroid/fdroiddata`

F-Droid volunteers then review it. They may ask for changes; that is normal.
Once merged, the first build usually appears within a day or two.

## After it is published

To release a new version later: raise `versionCode` and `versionName` in
`app/build.gradle.kts`, add a changelog file named after the new `versionCode`
under `fastlane/metadata/android/en-US/changelogs/`, then tag the commit
(`git tag v2.2.0 && git push origin v2.2.0`). F-Droid watches the tags and picks
it up on its own — no second merge request needed.

Note that F-Droid signs with its own key, not the one used for the APKs built
here. Anyone running a sideloaded build has to uninstall it before they can
install the F-Droid one.
