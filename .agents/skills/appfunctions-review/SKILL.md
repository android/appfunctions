---
name: appfunctions-review
description: >-
  Reviews pull requests and local changes in the android/appfunctions repo.
  Use when asked to review a PR, branch, or diff in this repo.
---

# AppFunctions Review

Review new or changed code only.

## Checks

1. **Architecture**: Verify the change follows the
   [Guide to app architecture](https://developer.android.com/topic/architecture),
   especially unidirectional data flow. Flag:
   - A ViewModel that exposes anything other than a single read-only
     `StateFlow<UiState>`, or a UI that changes state instead of sending events.
   - Business logic, permission checks, or data fetching inside Composables.
   - Code in the wrong layer (`data/`, `domain/`, `ui/`), or config and data
     hardcoded in a ViewModel.
2. **Compose**: Flag stateful child Composables where state could be hoisted
   (pass values down, lambdas up), UI state that should survive configuration
   changes but doesn't use `rememberSaveable`, and new UI components without a
   `@Preview`.
3. **Code quality**: Flag dependency versions not in `libs.versions.toml`,
   user-facing strings not in `strings.xml`, ViewModels or UseCases not
   provided by Hilt, and important public APIs without KDoc.
4. **Tests**: Flag new or changed ViewModels and UseCases without unit tests
   for both success and error states. Prefer behavior checks over interaction
   checks, real objects or fakes over mocks, and Robolectric over heavy mocking
   of Android classes. Complex UI should have screenshot tests.
5. **LLM payload**: Verify that the payload sent to the LLM on each turn is
   sensible. If the change affects what is sent, trace the code and write out
   the actual payload, before and after the change, for one user turn and one
   tool-result turn. Flag anything that is unnecessary, unbounded, sensitive, or
   misleading to the model.

## Output

List only findings that matter, as `file:line: problem → suggested fix`.
Prefix suggestions the author can reasonably skip with `[Optional]`.
If nothing matters, reply "LGTM". Ask before posting to GitHub.
