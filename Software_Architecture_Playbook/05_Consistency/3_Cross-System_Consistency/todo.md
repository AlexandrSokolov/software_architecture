## Messages that always fail

- One message fails every time you handle it. What happens to the messages behind it?
- How do you tell a message that will never work from one that failed because the DB was down for a second?
- How many times do you retry, and how long do you wait between tries?
- Where do messages that keep failing go, and who looks at them?
- You fixed the bug. How do you handle the failed messages again safely?
