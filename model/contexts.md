## Context map

### Author

Authoring Quizzes is a separate activity which doesn't depend on any other parts.
Integrity is provided by the only entity

 * `Quiz` - entity and root

Implemented by module **`author`**.

See

 * [Quiz entity](../author/src/main/scala/QuizEntity.scala)

### School

Controls usage of Quizzes, supports scheduling Exams.

 * `School` - root
 * `QuizFact` - entity, controls Quiz usage
 * `Exam` - entity

Implemented by module **`school`**.

See

 * [QuizFact entity](../school/src/main/scala/QuizFactEntity.scala)
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

