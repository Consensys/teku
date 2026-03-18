---
name: review-pr
description: Fetch and address PR review comments for the current branch
---

# Review PR Comments

Find the PR for the current branch and process all inline review comments one by one.

## Steps

1. **Find the PR**: Run `gh pr view --json number,url,title` to get the PR associated with the current branch. If no PR exists, inform the user.

2. **Fetch comments**: Use `gh api repos/{owner}/{repo}/pulls/{number}/comments` to get all inline review comments.

3. **For each comment**, display:
   - File path and line number
   - Reviewer name
   - The comment text
   - The current code at that location

4. **Propose an action** for each comment:
   - If you agree: explain what you'll change and why
   - If you disagree: explain why the current code is correct
   - If you need clarification: ask the user

5. **Wait for user confirmation** before applying each change. The user may:
   - Agree with your proposal
   - Disagree and suggest a different approach
   - Skip the comment

6. After all comments are processed:
   - Run `./gradlew spotlessApply`
   - Run relevant tests
   - Summarize what was addressed and what was skipped