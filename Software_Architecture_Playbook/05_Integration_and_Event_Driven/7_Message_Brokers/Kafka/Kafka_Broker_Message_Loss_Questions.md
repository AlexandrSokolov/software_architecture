## Kafka Broker Message Lost Questions
<details><summary>Show questions</summary>

### Follower falls behind

- A follower was down for an hour. How does it get the messages it missed?
- The leader already deleted the messages a returning follower needs. What happens to that follower?
- A follower never comes back. Does the leader keep its log forever?
- Your consumer was down longer than the broker keeps messages. What happens?
- A consumer was offline while events were published. Does it get them later?

### In-sync copies run out

- An in-sync follower dies. Do `acks=all` writes hang?
- The leader waits for all copies, yet a message is lost when it dies. How?
- Only the leader is left in sync. Should it keep taking writes?

### Leader dies

- The leader confirmed the message, then died before copying it to the others. Why is the message gone?
- A copy that was behind becomes the new leader. What happens to messages only the old leader had?
- The leader is cut off from the cluster but producers still reach it. What's lost?
- How do producers find the new leader after a failover?

### All copies at once

- All three copies lose power at the same moment. Why can a confirmed message vanish?

</details>

