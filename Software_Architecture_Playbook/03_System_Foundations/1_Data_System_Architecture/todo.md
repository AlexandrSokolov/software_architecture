## Chaos

Here's the reading order with each prerequisite placed right before the section that needs it:

1. Problems with Distributed Systems (20) → cards in `03_Data_Systems/1_Data_System_Architecture`
2. **Microservices and Serverless (21)**
3. Systems of Record and Derived Data (10) → cards in `03_Data_Systems/1_Data_System_Architecture`
4. Keeping Systems in Sync (501) → cards in `06_Architecture_Traps`
5. Two-Phase Commit (324) → cards in `04_Distributed_Data/8_Distributed_Transactions`
6. **Distributed Transactions Across Different Systems (328)**
7. Implementation of Replication Logs (206) → cards in `04_Distributed_Data/6_Replication`
8. **Change Data Capture (503)**
9. **Durable Execution and Workflows (187)**

The four bold ones are the cards for `4_Microservices_and_Sagas`.

What do you want to do with them?

## Databases Backed by Object Storage
Object storage can be used for more than archiving data. Many databases are begin‐
ning to use object stores such as Amazon S3, Google Cloud Storage, and Azure Blob
Storage to serve data for live queries. Storing database data in object storage has many
benefits:
202
| Chapter 6: Replication• Object storage is inexpensive compared to other cloud storage options. This
allows cloud databases to store data that’s queried less often on cheaper, higher-
latency storage while serving the working set from memory, SSDs, and NVMe.
• Object stores provide multi-zone, dual-region, or multi-region replication with
very high durability guarantees. This also allows databases to bypass inter-zone
network fees.
• Databases can use an object store’s conditional write feature—essentially, a
compare-and-set (CAS) operation—to implement transactions and leadership
election [10, 11].
• Storing data from multiple databases in the same object store can simplify data
integration (see “Cloud Data Warehouses” on page 135), particularly when open
formats such as Parquet and Iceberg are used.
These benefits dramatically simplify the database architecture by shifting the respon‐
sibility of transactions, leadership election, and replication to object storage.
Systems that adopt object storage for replication must grapple with trade-offs,
though. Notably, object stores have much higher read and write latencies than local
disks or virtual block devices such as Amazon EBS. Many cloud providers also charge
a per-API call fee, which forces systems to batch reads and writes to reduce cost. Such
batching further increases latency. Objects are often immutable as well, which makes
random writes in a large object an extremely resource-intensive operation. Finally,
many object stores do not offer standard filesystem interfaces, which prevents systems
that lack object storage integration from leveraging object storage. Interfaces such
as filesystem in userspace (FUSE) allow operators to mount object store buckets as
filesystems that applications can use without knowing their data is stored on object
storage. Still, many FUSE interfaces to object stores lack POSIX features such as
nonsequential writes or symlinks, which systems might depend on.
Different systems deal with these trade-offs in various ways. Some introduce a tiered
storage architecture that places less frequently accessed data on object storage, while
new or frequently accessed data is kept on faster storage devices such as SSDs or
NVMe, or even in memory. Other systems use object storage as their primary storage
tier but use a separate low-latency storage system (such as Amazon EBS or Neon’s
Safekeepers [12]) to store their WAL. Recently, some systems have gone even further
by adopting a zero-disk architecture (ZDA). ZDA-based systems persist all data to
object storage and use disks and memory strictly for caching. This allows nodes to
have no persistent state, which dramatically simplifies operations. WarpStream, Con‐
fluent Freight, Buf ’s Bufstream, and Redpanda Serverless are all Kafka-compatible
systems built using a zero-disk architecture. Nearly every modern cloud data ware‐
house also adopts such an architecture, as does Turbopuffer (a vector search engine)
and SlateDB (a cloud native LSM storage engine).

## 