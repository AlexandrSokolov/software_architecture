# Data Modeling Books — Reading List

Goal: deeper knowledge of data modeling in general, beyond DDIA Ch. 3.

**How to read this file**
- **Checked** = found in a web source during this session (Redgate book lists, publisher pages). The Redgate lists are
  from 2021, so newer editions may exist. Editions were not checked.
- **Not checked** = from my own knowledge. Title, author or edition may be slightly off. Verify before buying.

---

## 1. General data modeling (any database)

| Book                                         | Authors                          | Level        | What it gives you                                                                  | Status      |
|----------------------------------------------|----------------------------------|--------------|------------------------------------------------------------------------------------|-------------|
| Data Modeling: A Beginner's Guide            | Andy Oppel                       | Beginner     | Step-by-step examples, exercises, a test after each chapter                        | Checked     |
| Data Modeling Made Simple, 2nd ed.           | Steve Hoberman (author: not checked) | Beginner | Practical, for business and IT people. Feels incomplete for a full-time modeler    | Checked     |
| Database Modeling Step by Step               | Gavin Powell                     | Beginner     | Normalization explained clearly, how logical models become real databases          | Checked     |
| Data Modeling Essentials, 3rd ed.            | Graeme Simsion (and Graham Witt) | Intermediate | The most complete one: ER and UML, normal forms, time-dependent data, business rules. Thick book | Checked |
| The Data Modeling Handbook                   | Michael Reingruber, William Gregory | Intermediate | Rules to judge if your model is good, several notations compared              | Checked     |
| Data Model Patterns                          | David Hay                        | Advanced     | Ready models for typical business areas (accounting, manufacturing)                | Checked     |
| The Data Model Resource Book, Vol. 1, 2, 3   | Len Silverston                   | Advanced     | A library of universal models, compared to the "Gang of Four" books for patterns   | Checked     |
| Data and Reality                             | William Kent                     | Advanced     | Why every model is imperfect. Ideas, not a how-to. Read after you designed a few databases | Checked |

## 2. Relational design (tables, keys, normal forms)

| Book                                                 | Authors                            | Level        | What it gives you                                                          | Status  |
|------------------------------------------------------|------------------------------------|--------------|----------------------------------------------------------------------------|---------|
| Database Design for Mere Mortals, 25th Anniversary (4th ed., Addison-Wesley, Dec 2020) | Michael J. Hernandez | Beginner | Simplest way to learn relational design: tables, keys, relationships, business rules | Checked |
| Database Design and Relational Theory                | C. J. Date                         | Intermediate | Normal forms and the theory behind them, written for practitioners         | Checked |
| Database Design Using Entity-Relationship Diagrams   | Sikha Bagui, Richard Earp          | Intermediate | ER diagrams from zero, with exercises and case studies                     | Checked |
| Database Modeling and Design: Logical Design         | Toby Teorey and others             | Advanced     | ER, UML, normalization, logical design. Wide, mostly theory                | Checked |
| Information Modeling and Relational Databases        | Terry Halpin                       | Advanced     | Object Role Modeling (ORM). Academic style                                 | Checked |
| Database Design for Smarties                         | Robert J. Muller                   | Advanced     | UML for data modeling, from requirements to schema                         | Checked |
| Database Systems: The Complete Book                  | Garcia-Molina, Ullman, Widom       | Intermediate | Design for application developers, then how a database works inside        | Checked |

## 3. Analytical modeling (warehouse)

| Book                                                          | Authors                 | What it gives you                                                              | Status  |
|---------------------------------------------------------------|-------------------------|--------------------------------------------------------------------------------|---------|
| The Data Warehouse Toolkit, 3rd ed. (Definitive Guide to Dimensional Modeling) | Ralph Kimball, Margy Ross | Star schema patterns, 12 case studies, two ETL chapters. The standard book for dimensional modeling | Checked |
| Building a Scalable Data Warehouse with Data Vault 2.0        | Dan Linstedt, Michael Olschimke | Data Vault method. Rare in interviews                                    | Not checked |

## 4. Books already named in the Playbook (second-tier, one technique each)

| Book                                                    | Authors                    | Covers                                              | Status      |
|---------------------------------------------------------|----------------------------|-----------------------------------------------------|-------------|
| SQL Antipatterns                                        | Bill Karwin                | Trees in SQL, EAV, polymorphic associations         | Not checked |
| Trees and Hierarchies in SQL for Smarties               | Joe Celko                  | Nested sets, materialized path                      | Not checked |
| Developing Time-Oriented Database Applications in SQL   | Richard Snodgrass          | Bitemporal modeling                                 | Not checked |
| The DynamoDB Book                                       | Alex DeBrie                | Single table design, query-first design             | Not checked |
| NoSQL Distilled                                         | Pramod Sadalage, Martin Fowler | Key-value, document, column, graph families     | Not checked |
| Graph Databases                                         | Ian Robinson, Jim Webber, Emil Eifrem | Property graph modeling                  | Not checked |

---

## 5. My opinion for interview prep (not from a source)

- None of the general books is needed for the interview. DDIA Ch. 3 plus the Playbook folders cover the questions.
- One gap remains: "draw the data model for X" is a common task, and no Playbook book teaches it. For that,
  pick one general book, not several.
- Easy start: *Database Design for Mere Mortals*. Deep and complete: *Data Modeling Essentials*.
- Skip for now: Data Vault, Silverston, Hay, Kent, Halpin. They are for modeling as a full-time job.
- Skipped from the Redgate lists: books tied to one product (MS Access, MySQL, SQL Server) and "For Dummies" titles.
