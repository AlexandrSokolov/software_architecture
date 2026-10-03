## Claude

Yes, I know it. It's the timeline example: when a user opens the app, show the latest posts from everyone they follow. It's in the book to show how to describe load, and the trade-off it teaches is one of the most reused system design questions. "Design Twitter", "design a news feed" and "design an Instagram feed" are all this problem. Alex Xu's *System Design Interview* has a whole chapter on it. So yes, it deserves more attention.

The core of it:

- **Two ways to build a timeline.** Compute it at read time by querying the posts of everyone the user follows. Or precompute it: when someone posts, write that post into every follower's timeline cache (field term: *fan-out on write*).
- **The trade-off.** Precomputing makes reads cheap, but one post turns into thousands of writes. Reading at query time makes posting cheap, but every page load does heavy work. Reads far outnumber posts, which favors precomputing.
- **The catch.** A user with millions of followers turns one post into millions of writes. The usual fix is a mix: precompute for normal users, and fetch celebrity posts at read time and merge them in.
- **The load number that decides it:** how many followers each user has, plus the ratio of reads to posts, not just requests per second.

I don't need the book's text to write cards on this. One caveat: I know the 1st edition's version in detail (Twitter, 2012 numbers). The 2nd edition reworks it as a generic social network with new numbers, and I'm not sure of those exact figures. If a card uses numbers, take them from your copy.

Possible cards:

| Card                                                                                                       | Where                                                                  |
|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| "Build a home timeline — two ways?"                                                                        | `3.2.6_Scalability.md`, next to "How do you describe a system's load?" |
| "Precomputed timelines — what do they cost?" (writes per post, storage, delay before followers see a post) | same                                                                   |
| "One user, 30 million followers — what breaks?"                                                            | same                                                                   |
| "Which load number decides the timeline design?"                                                           | same                                                                   |
| "Design a news feed — where do you do the work?" (verdict / reasoning / trade-off)                         | `Software_Architecture_Scenarios/`                                     |

Want me to draft them?

## Gemini

I know this example perfectly. You do not need to paste the content.

This is the famous **Twitter Home Timeline (Fan-Out)** case study from DDIA Chapter 1 (under "Describing Load"). You are absolutely right to flag it: "Design Twitter" or "Design a News Feed" is arguably the single most common System Design interview question in the world.

Interviewers use this exact scenario to test if you understand **trade-offs between read and write heavy architectures**.

Here is the exact progression of the trap the book describes, and how interviewers expect you to solve it:

1. **Approach 1: Fan-out on Read (Pull)**
* *How it works:* When you open your timeline, the system queries the database for all the people you follow, fetches their tweets, merges them, and sorts them by time.
* *The Problem:* Reads are agonizingly slow because the system has to do a massive relational `JOIN` every time someone opens the app.


2. **Approach 2: Fan-out on Write (Push)**
* *How it works:* Every user has a pre-computed "timeline cache" (a mailbox). When you tweet, the system looks up your followers and pushes your tweet into all of their mailboxes. When they open the app, reading is instant.
* *The Problem (The Justin Bieber Trap):* A celebrity with 30 million followers tweets. The system suddenly has to execute 30 million cache inserts for a single write. The system grinds to a halt.


3. **Approach 3: The Hybrid Solution (The Interview Answer)**
* *How it works:* Normal users fan-out on write (push). Celebrities fan-out on read (pull). When you open your timeline, you instantly get your cached mailbox (normal friends) and the system separately fetches celebrity tweets and merges them at read time.



This case study is the definitive example of a **load parameter** (number of followers per user) dictating an architectural shift.

We should absolutely create a dedicated cluster of flashcards for this scenario. It belongs perfectly in your **Scalability** file (which I believe is currently your `2.3_Nonfunctional_Requirements.md`), as it perfectly illustrates how to measure and handle load.

Would you like me to generate this specific "Fan-Out / Timeline Scenario" flashcard cluster now?