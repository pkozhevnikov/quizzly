## Context map

### Author

Authoring Quizzes is a separate activity which doesn't depend on any other parts.

 * `Main` - application service
 * `Quiz` - entity and root
 * `SectionEdit` - entity
 * `Read` - read side component

Implemented by module **`author`**.

Application service provides public HTTP API, translates requests to commands and transmits them
to entities. Quiz entity controls consistence of the Quiz state, but commands that may be processed
by SectionEdit entity are sent to SectionEdit directly.

Read side component listens to changes of Quizzes and registers them in the Quiz list. Application 
service redirects requests for quiz list to the Read side.

See

 * [Quiz entity](../author/src/main/scala/QuizEntity.scala)
 * [Section entity](../author/src/main/scala/SectionEditEntity.scala)

### School

Controls usage of Quizzes, supports scheduling Exams.

 * `School` - root
 * `QuizFact` - entity, controls Quiz usage
 * `Exam` - entity

Implemented by module **`school`**.

See

 * [QuizFact entity](../school/src/main/scala/QuizFact.scala)
 * [Exam entity](../school/src/main/scala/ExamEntity.scala)
 * [School root](../school/src/main/scala/School.scala)

### Trial

Controls Trial and Practice sessions by means the only entity in different modes

 * `Trial` - entity and root

Implemented by module **`trial`**.

See

 * [Trial entity](../trial/src/main/scala/TrialEntity.scala)

### Registration and authentication

Controls the registration process and provides authentication service.

 * `Auth` - root
 * auth service
 * `Registration` - entity, controls registration process

Implemented by module **`auth`**.

---
### Communication and other supporting functionality
TBD

