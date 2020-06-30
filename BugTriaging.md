# Teku Bug Triage Process

In Teku, we have a well-defined policy regarding bug categorization. In a nutshell, our bugs are categorized into 5 priorities. 

#### Bug Priorities

| Priority | Examples |
|-|-|
| Very High (P1) | <ul><li>Affects consensus</li> <li>Breaks sync</li><li>Security issue</li></ul> |
| High (P2) | <ul><li> Degrades validator rewards</li> <li>Unexpected behaviour of core features</li></ul>|
| Medium (P3) | <ul><li>Feature not working with a specific client library</li></ul>|
| Low (P4) | <ul><li>Node doesn't start up when the configuration file has unexpected "end-of-line" character</li></ul> |
| Very Low (P5)  | <ul><li>Typo on a CLI command description</li> <li>An inconsequential issue gets logged on ERROR level</li></ul>|

#### Why is it important?
All software has bugs. And sometimes we don't find them. This is something that we need to live with. However, when we do find bugs we need to do our best to identify the potential impact of that bug in our system.

A proper triaged bug helps up to prioritize and act on bugs in a timely manner. Also, by analysing the severity and impact of our bugs, we can create/update processes to ensure we are better prepared for them in the future.

#### How to triage a bug?
All of our bugs are reported on GitHub issues. They are identified by the BUG label. We can make use of GitHub labels to categorize our bugs. We have 5 labels, P1, P2, ..., P5 to categorize a bug according to our [policy](#Bug-Priorities) shown above.

##### Step-by-step process (for maintainers)
1) Identify that a bug doesn't have a priority (no Px label).
2) Use the bug categorization policy to determine the final priority of the bug.
3) Based on your assessment, add the corresponding label to the bug.
(optional) Add a comment to the issue explaining the rationale (justifying the severity and probability analysis).

#### FAQ
##### What if I don't agree with the priority of a bug?
We try to be deterministic on our assessments, and most of the time it will involve some discussion. However, if you feel like the assessment on a bug priority is wrong, you can always add a comment on the issue and start a discussion. There is also the option of starting a conversation on Rocket Chat.

##### Can I change the priority of a bug?
You can. However, if another person was responsible for the initial assessment it is expected that you will contact that person to share your thoughts before changing it.

It is a combination of being polite and reasonable. :)

##### What if I don't have enough knowledge of a specific area to prioritize a bug?
That's ok! Deeper understanding of the impact of a bug will come from the people that work closer to the affected area of the code. Leave it to them!



_Mostly taken from Besu Docs_: [Besu Bug Triage Process](https://wiki.hyperledger.org/display/BESU/Bug+Triage+Process)