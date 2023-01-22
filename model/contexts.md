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

 * `QuizFact` - entity, controls Quiz usage
 * `Exam` - entity
 * `ExamTracker` - single entity, manages and tracks state of Exams

Implemented by module **`school`**.

See

 * [QuizFact entity](../school/src/main/scala/QuizFact.scala)
 * [Exam entity](../school/src/main/scala/ExamEntity.scala)
 * [ExamTracker entity](../school/src/main/scala/ExamTracker.scala)

`ExamTracker` is a singleton entity, that registers creation of new exams, periodically checks
if an exam is about to progress its state from `Pending` to `Upcoming` and from `Upcoming` to
`InProgress`. If time till progress is equal to or less than configured value the `ExamTracker`
notifies the `Exam` to 'awake' it if it's passivated.  When an `Exam` proceeds to next state,
the `ExamTracker` registers the state change. On change to `InProgress` or `Cancelled` state,
`ExamTracker` stops tracking the exam and removes it from the tracked exams list.

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

