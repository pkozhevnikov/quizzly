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

Entities:

 * `Trial` - controls a trial for an Exam of specific Testee
 * `Exam` - local representation of an Exam from the *school* module

Implemented by module **`trial`**.

Every Quiz that is released by *author* module is sent to the *trial* module entirely.

#### Trial workflow

When an Exam transits to the InProgress state, the *school* module sends a notification about
started Exam to all testees, and all exam attributes to the *trial* module.

The exam page displays main attributes of the exam and the button for start of the trial. Clicking
the button creates new Trial entity. The Trial is limited in time by *trial length* attribute
of the Exam. Every page of the trial corresponds to a section of the exam quiz. The testee
submits her solution of every quiz item. Submitted item cannot be re-submitted again. After all
items are submitted, the testee is enabled to move to the next section. When all sections are
submitted, the trial is finished. After trial length passed and the trial is not yet finished,
the trial is finished automatically.

During all trial remaining time is displayed.

See

 * [Trial model](../trial/src/main/scala/Trial.scala)
 * [Trial entity](../trial/src/main/scala/TrialEntity.scala)
 * [Exam entity](../trial/src/main/scala/ExamEntity.scala)

### Registration and authentication

Controls the registration process and provides authentication service.

 * `Auth` - root
 * auth service
 * `Registration` - entity, controls registration process

Implemented by module **`auth`**.

---
### Communication and other supporting functionality
TBD

