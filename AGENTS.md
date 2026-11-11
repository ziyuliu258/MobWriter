# Repository rules

MobWriter is a general-purpose Hexo writing app. Keep its source and documentation independent of any individual user's infrastructure.

- Never hardcode personal IP addresses, domains, email addresses, account names, repository names, home-directory paths, credentials, or private article content.
- Personal development values belong only in ignored `config/*.local.*` files. Commit empty `*.example.*` templates instead.
- Keep personal values out of application defaults, resources, generated assets, APKs, and commit messages. Users configure their blog inside the app. Commit authors follow the user's Git identity.
- Deployment templates must use Hexo configuration and GitHub Secrets rather than personal servers or fixed deployment layouts.
- Never print private configuration values while checking them. Report file locations and issue categories only.
- Before committing or pushing, run `python3 scripts/check-privacy.py` against the staged files and confirm that private configurations, build output, and APKs are ignored.
- Run `./gradlew :app:assembleDebug :app:lintDebug :core:test` after changes to Kotlin, Gradle, or Android resources. Real local blog fixtures are opt-in.
