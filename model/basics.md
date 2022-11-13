# Quizzly

A system to author, maintain, organize and perform testing for education.

## Basic terms

 * **Person**s - people that take *place* in the system, places are:
   * **Official**s - Persons that organize testing;
   * **Student**s - Persons that are supposed to be tested;
 * **Quiz** - a set of test questions;
 * **Exam** - a scheduled event of taking a specific Quiz by **Testee**s - Students and Officials
 that are *listed* on the Exam;
 * **Trial** - a session of taking a Quiz within an Exam;
 * **Practice** - a session of taking a Quiz for practicing.

## Main activities
  
Persons *register* in the system through the **Registration** process.

Officials author Quizzes.

Officials schedule Exams.

Testees take Exams.

Quizzes may be published for practicing, Persons may take practicing Quizzes.

### Registration

**Registration** is initiated by **Invitation** made by any Official: an Official can invite
other Person specifying place, email and optionally name of a person. The invited person receives
an email message with Invitation, follows a **Registration Link**, specifies name (if required)
and **Nickname**, and *confirms* the Registration. The Official who made the Invitation receives
a notification of Registration, checks and *accepts* or *rejects* Registration. Invited person
receives a notification of resolution.  If the Registration is accepted, the notification
contains a link to the **Set password page**.  The Person specifies new password and gets access
to the system.

Invitation expires if Registration is not confirmed within configured timespan.

Invitation to the **First Official** is sent automatically to configured email during the
first run of the system.  Registration of the First Official is accepted automatically.

Related links:

 * [Persons](persons.md) - more details on persons

### Quiz authoring

An Official creates a new **Quiz**, specifying:

 * one or more **Author**s from Officials
 * one or more **Inspector**s from Officials

The Official that created a new Quiz is the **Curator** of this Quiz.

Roles within a Quiz:

 * Curator - creates the Quiz, assigns Authors and Inspectors, deletes the Quiz
 * Author - designs and builds the Quiz
 * Inspector - assesses quality of the Quiz

The same Official cannot play two different roles within a Quiz.

*States* of a Quiz:

 * **Composing** - Curator created the Quiz, Authors and Inspectors are notified of new Quiz,
 the Quiz is available for changes by Authors;
 * **Review** - All Authors report about readiness of the Quiz, Quiz gets read-only, Curator and
 Inspectors are notified of *readiness* of the Quiz, Inspectors *review* the Quiz and *approve*
 or *disapprove* the Quiz.  If the Quiz is disapproved by all Inspectors, Quiz returns to the
 Composing state, Curator and Authors are notified of disapproval the Quiz;
 * **Released** - All Inspectors approve the Quiz, the Quiz gets read-only forever.

An Official may duplicate a released Quiz in order to create new Quiz. This Official is a
Curator of copy Quiz, the copy Quiz is of Composing state.

Curator may mark a Quiz **Obsolete**.

Related links:

 * [Availability of Quizzes](quizzes.md) - details of usage Quizzes in Exams and Practicing
 * [Quiz model](quiz-model.md)
 * [User stories for Quiz authoring](../author/src/test/scala/quizzly/accept/QuizAuthoringSpec.scala)

### Exam management

An Official creates an Exam. This Official is the **Host** of the Exam. Host specifies a
Released not Obsolete Quiz and sets the **Exam Period** - a period Testees are eligible to take
the Exam within. Quiz and Exam Period are set on Exam creation only. There is a configurable
settings **Preparation Interval** - time interval before Exam starts - which is applied to the
Exam automatically.

*States* of an Exam:

 * **Pending** - Host created the Exam, specifying a Quiz, **Exam Period** - a period
Testees are eligible to take the Exam within, **Trial Length** - time period a Testee 
is allowed to *submit* her Trial within. Host is able to change Trial Length and include/exclude
Testees in/from the Exam till start of Exam Period minus Preparation Interval;
 * **Upcoming** - On the Preparation Interval start, the Exam is disabled for any changes
and all Testees included in the Exam receive the **Exam Notification** of upcoming Exam;
 * **In Progress** - During Exam Period, every Testee is able to take the Exam once;
 * **Ended** - On Exam Period end, the Exam is done and any Trial submissions are rejected;
 * **Cancelled** - Host is able to cancel the Exam before Exam Period. 
 
Testee is notified of her inclusion/exclusion in/from the Exam.

An Exam may be duplicated in order to create another exam keeping the Testee list.

Related links:

 * [Availability of Quizzes](quizzes.md) - details of usage Quizzes in Exams and Practicing
 * [User stories for Exam management](../school/src/test/scala/quizzly/accept/ExamManagementSpec.scala)

### Trial

Beginning from Exam Period start time till Exam Period end minus Trial Length, the Exam is
available for Trials. A Testee follows a link provided by Exam Notification and starts her
Trial. The Testee submits every solution during the Trial.  When the Testee has done with the
Quiz, she *finalize*s the Trial. If the Testee doesn't finalize the Trial during Trial Length,
the Trial is finalized automatically at Trial start time plus Trial Length.

### Practice

An Official adds a released Quiz to **Practicing Quiz** list. 

A Quiz may be added to the Practicing Quiz list if:

 * a Quiz is not used by an Exam;
 * a Quiz is used by ended or cancelled Exam and marked Obsolete.

During Practice session, a Quiz is not time limited, on every submission, a solution is shown. A
user may start a Quiz in *exam simulation* mode, in this case, the Quiz is time limited,
solutions are not shown and the score is shown after the Quiz is finalized.

Related links:

 * [Availability of Quizzes](quizzes.md) - details of usage Quizzes in Exams and Practicing



