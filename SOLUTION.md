# Solution: Build, Run and Pass All Tests

## Summary

As delivered, the project did not build. Every test class was marked `@Ignore`, and most of the API was placeholders. The work fell into three parts:

1. **Get it building and running.** One dependency could no longer be downloaded, the 2018-era build tooling does not support current JDKs, and the server crashed on startup because of a library mismatch.
2. **Fix existing bugs.** Validation was inverted, so nobody could register or log in. Error responses were unusable, and following someone was recorded in one direction but read in the other.
3. **Implement the missing features and enable the tests.** Articles, comments, favorites, feed, profiles and tags are now implemented, and the `@Ignore` annotations have been removed.

**Result:**
- All **25 JUnit tests pass**, with none skipped.
- The project's RealWorld API spec suite (`spec-api`, run with Newman) passes all **280 assertions across 31 requests**, with 0 failures.

## How to Run

Requirements: JDK 21. The Gradle wrapper downloads Gradle and Kotlin itself. Node.js is needed only for the spec suite.

```sh
./gradlew clean build   # compile and run all tests
./gradlew run           # start the server on http://localhost:8080

# RealWorld API spec suite (server must be running)
APIURL=http://localhost:8080/api ./spec-api/run-api-tests.sh
```

All routes are served under `/api`, as in the RealWorld spec.

### Example Requests

With the server running (`./gradlew run`). The database is in memory, so it starts empty on every run.

```sh
# Register two users
curl -H 'Content-Type: application/json' \
  -d '{"user":{"email":"alice@example.com","username":"alice","password":"secret"}}' \
  http://localhost:8080/api/users
curl -H 'Content-Type: application/json' \
  -d '{"user":{"email":"bob@example.com","username":"bob","password":"secret"}}' \
  http://localhost:8080/api/users

# Log in as alice and save the JWT
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"user":{"email":"alice@example.com","password":"secret"}}' \
  http://localhost:8080/api/users/login | sed -E 's/.*"token":"([^"]+)".*/\1/')

# Current user
curl -H "Authorization: Token $TOKEN" http://localhost:8080/api/user

# Create an article (slug is generated from the title)
curl -H 'Content-Type: application/json' -H "Authorization: Token $TOKEN" \
  -d '{"article":{"title":"How to train your dragon","description":"Ever wonder how?","body":"Very carefully.","tagList":["dragons","training"]}}' \
  http://localhost:8080/api/articles

# Public reads (no token needed)
curl http://localhost:8080/api/articles
curl "http://localhost:8080/api/articles?tag=dragons&author=alice"
curl http://localhost:8080/api/articles/how-to-train-your-dragon
curl http://localhost:8080/api/tags

# Favorite the article
curl -X POST -H "Authorization: Token $TOKEN" \
  http://localhost:8080/api/articles/how-to-train-your-dragon/favorite

# Comment on it, then list comments
curl -H 'Content-Type: application/json' -H "Authorization: Token $TOKEN" \
  -d '{"comment":{"body":"Thanks for sharing!"}}' \
  http://localhost:8080/api/articles/how-to-train-your-dragon/comments
curl http://localhost:8080/api/articles/how-to-train-your-dragon/comments

# Follow bob, then view his profile and alice's feed
curl -X POST -H "Authorization: Token $TOKEN" http://localhost:8080/api/profiles/bob/follow
curl -H "Authorization: Token $TOKEN" http://localhost:8080/api/profiles/bob
curl -H "Authorization: Token $TOKEN" http://localhost:8080/api/articles/feed
```

The feed shows articles by users you follow, so alice's feed stays empty until bob publishes something.

### Running the Tests and Viewing Results

Each test starts a real server on a free random port, so the tests can run while `./gradlew run` is using 8080.

```sh
./gradlew clean test
```

`build.gradle` enables test logging, so each test's result is printed to the terminal:

```
ArticleControllerTest > create article PASSED
CommentControllerTest > add comment for article by slug PASSED
...
UserControllerTest > success register user PASSED
BUILD SUCCESSFUL
```

There is also an HTML report with all tests grouped by class, each with its status and duration:

```sh
open build/reports/tests/test/index.html
```

To run one test class or a single test:

```sh
./gradlew test --tests "*TagControllerTest"
./gradlew test --tests "*ArticleControllerTest.create article"
```

## Part 1: Build and Startup Fixes

| Problem | Fix |
|---|---|
| **Dependency could not be downloaded.** Exposed `0.14.1` was only published to JCenter, which has shut down. | Upgraded to Exposed `0.41.1`, which is on Maven Central (`exposed-core`, `exposed-jdbc` and `exposed-java-time`). |
| **Build tooling did not support current JDKs.** Gradle 4.10 and Kotlin 1.3 cannot build on JDK 21, and `kotlin_version = "1.3.+"` caused a Kotlin standard library clash. | Upgraded to Gradle 8.10.2 (wrapper regenerated) and Kotlin 1.9.25. Rewrote `build.gradle` with the `plugins {}` block, `implementation`/`testImplementation` and `jvmToolchain(21)`, and turned on per-test result logging. Removed the `jcenter()` repository. |
| **Server crashed on startup** with `NoSuchMethodError: JdbcConnection.getSession()`. Dependabot had bumped H2 to 2.x, which older Exposed versions do not support. | Fixed by the Exposed upgrade, which needed a package rename and the new `PrimaryKey(...)` syntax. This was preferred over downgrading H2 to 1.4.x, which has known CVEs. |

Ktor (1.2.3), Kodein and the other libraries were left at their existing versions to keep the change small.

## Part 2: Bug Fixes in Existing Code

| Problem | Fix |
|---|---|
| **Registration and login always failed.** The checks in `User.kt` required the password and username to be **blank**. The update check required every field to be blank. | Corrected the register and login checks. The update check now requires only a valid email, since partial updates are supported. |
| **Error responses were unusable.** Every exception returned 500 with a body like `SuspendFunctionGun@d65c9c`. | `StatusPages` now returns the real message in the RealWorld error format: 422 for validation errors, 401 for failed login, 403 for forbidden, 404 for not found, 500 otherwise. |
| **Follow direction was inconsistent.** `follow` stored the pair one way round, while `unfollow` and the "is following" check read it the other way. Follow status was therefore always wrong, and unfollow never removed anything. | `Follows.user` is now always the user being followed and `Follows.follower` is the follower. Following twice is now a no-op instead of a primary-key error. |
| **Password changes were stored unhashed.** `PUT /user` saved a new password as plaintext, so the user could no longer log in. | New passwords are hashed the same way as at registration. The response also includes a fresh token, because the token holds the email address, which may have changed. |
| **Route protection was wrong.** Public reads such as article lists, single articles and comments required login. | Routes are restructured so reads use optional login, and writes and the feed require login. |

## Part 3: Implemented Features

The article, comment and profile controllers previously held only commented-out code. They now follow the existing controller → service → repository structure:

- **`ArticleRepository` / `ArticleService`:**
  - list articles with `tag`, `author`, `favorited`, `limit` and `offset` filters; `articlesCount` is the total number of matches, not the page size
  - feed of articles from followed users
  - get, create, update and delete articles
  - favorite and unfavorite
  - unique slugs are generated from the title, and change when the title changes
  - tags are stored and linked to articles
  - only the author can update or delete an article (403 otherwise)
- **`CommentRepository` / `CommentService`:** add, list and delete comments. Only the comment's author can delete it.
- **`ProfileController`:** get a profile (works without login), follow and unfollow.
- **Dates** are serialized as ISO 8601 (e.g. `2026-10-16T15:39:03.799+00:00`), as the spec requires.
- **Database setup.** Each server start now gets a fresh, uniquely named in-memory database, and all tables are created in one place (`DbConfig`). Previously each repository created its own tables at first use. An unused embedded H2 PostgreSQL server bound to a fixed port was also removed; it made the second server start in the same JVM fail, which broke the tests.

## Changes to the Tests

The test logic is unchanged except for two problems that made the tests impossible to pass as written:

1. **Inconsistent URL prefix.** `UserControllerTest` and the `HttpUtil` register and login helpers called `/users...`, while every other test called `/api/...`. Since the RealWorld spec uses `/api`, the server now serves everything under `/api`, and those six paths were updated to match.
2. **The feed test read the wrong user's feed.** In `get all articles of feed`, user A creates an article and user B follows A, but the test then read **A's** feed. A follows nobody, so a correct feed is empty and `first()` throws. The request was changed to read **B's** feed, which is what the test sets up.

One change to the test setup itself: `AppRule` used to start every test server on the fixed port 8080. If anything else was listening there, such as the app started with `./gradlew run`, the tests silently ran against that server and failed with misleading errors. `AppRule` now asks the OS for a free port, and `setup()` accepts an optional `port` parameter for this; the app still defaults to 8080.

Apart from removing `@Ignore`, no assertions were changed.

## Verification

- `./gradlew clean build`: **25/25 tests pass** (Article 14, Comment 3, Profile 3, Tag 1, User 4).
- RealWorld Newman suite: **280/280 assertions pass**.
- Manual checks of cases neither suite covers:

| Request | Result |
|---|---|
| List articles, get an article, get comments, without logging in | 200 |
| Get the feed without logging in | 401 |
| Another user updates or deletes someone else's article | 403 |
| Get an article that doesn't exist | 404 |
| Author deletes their own article, then gets it | 200, then 404 |

## Known Issues (Not Fixed)

- **Password exposed in responses.** Registration echoes the plaintext password, and the other user responses include the password hash. `UserControllerTest` asserts this behaviour, so changing it would mean changing those tests.
- **Weak password hashing.** Passwords are hashed with HMAC-SHA256 using a secret hardcoded in `Cipher.kt`, which is also the JWT signing key. A slow password hash such as bcrypt or Argon2 should be used, with secrets taken from configuration.
- **The article author is a `User`, not a spec `Profile`.** It includes `email` and `id` and lacks `following`. The test DTOs depend on the current shape.
- **Email regex is too strict.** It rejects valid addresses whose domain has a one-character label, such as `a@b.com`.
- **Article lists are slow at scale.** Each article in a list loads its tags, favorites and author with separate queries. This is fine for a demo but would need joins or batching for large lists.
- **Leftover configuration.** `application.conf` references a `setup` module and JWT settings that the code does not use (the server is started with `embeddedServer`).
