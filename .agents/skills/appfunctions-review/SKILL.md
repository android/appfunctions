---
name: appfunctions-review
description: >-
  Reviews pull requests and local changes in the android/appfunctions repo.
  Use when asked to review a PR, branch, or diff in this repo.
---

# AppFunctions Review Checklist

1. **Architecture**: Verify the change follows the
   [Guide to app architecture](https://developer.android.com/topic/architecture),
   especially unidirectional data flow.
2. **LLM payload**: Verify that the payload sent to the LLM on each turn is
   sensible.
