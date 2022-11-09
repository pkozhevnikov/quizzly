# Quizzly

A system to author, maintain, organize and perform testing for education.

## Basic terms

**Staff** - users that organize testing

**Student**s - users that are supposed to be tested

**Quiz** - a set of questions of a test

**Exam** - an event of quizzing **Student**s

**Course** - a group of **Student**s that are supposed to take specified **Exam**

**Trial** - a session of taking an **Exam**

## Main activities
  
All users are *registered* in the system through the **Registration** process.  **Staff** authors
**Quiz**zes. **Staff** forms **Course**s and schedules **Exam**s within **Course**s. **Student**s
take **Exam**s.

### Registration

**Registration** is initiated by **Invitation** made by any Staff user: a Staff user can invite
other user specifying **Purpose**(Staff or Student), email and optionally name of a person. The
invited person receives an email message with Invitation, follows a **Registration Link**,
specifies name (if required) and **Nickname**, and *confirms* the Registration. The user who
made the Invitation receives a notification of Registration, checks and *accepts* or *rejects*
Registration. Invited person receives a notification of resolution.  If the Registration is
accepted, the notification contains a link to the **Set password page**.  The user specifies 
new password and gets access to the system.

Invitation is expired if Registration is not confirmed within configured timespan.

Invitation to the **First Staff user** is sent automatically to configured email during the
first run of the system.  Registration of the First Staff user is accepted automatically.

Every user is able to change her email. A user makes a **Change Email Request**, receives a
validation message to new email address, then confirms new address. If new address is
confirmed successfully, email of the user changes. Change Email Request is expired if it is
not confirmed within configured timespan.

(TODO: the change name feature???)

### Quiz authoring

One of Staff users starts a new **Quiz**, specifying:

 * one or more **Author**s from Staff
 * one or more **Inspector**s from Staff

The Staff user that started a new Quiz is the **Curator** of this Quiz.

Roles of users within a Quiz:

 * Curator - starts the Quiz, assigns Authors and Inspectors, deletes the Quiz
 * Author - designs and builds the Quiz
 * Inspector - assesses quality of the Quiz

The same user cannot be of two different roles within a Quiz.

*Lifecycle* of a Quiz comprises the following *States*:

 * **Composing** - Curator started the Quiz, Authors and Inspectors are notified of new Quiz
 start, the Quiz is available for changes by Authors;
 * **Review** - All Authors report about readiness of the Quiz, Quiz gets read-only, Curator
 and Inspectors are notified of *readiness* of the Quiz, Inspectors *review* the Quiz and
 *approve* or *disapprove* the Quiz.  If the Quiz is disapproved, Quiz returns to the Composing
 state, Curator and Authors are notified of disapproval the Quiz;
 * **Released** - All Inspectors approve the Quiz, the Quiz gets read-only forever and is
 available for inclusion into Courses;
 * **Deleted** - The Quiz becomes obsolete and no longer corresponds to the learning path,
 Curator deletes the Quiz, Quiz is unavailable for inclusion into Courses, but still is available 
 for duplicating.

A Quiz is versioned: it may be duplicated, the duplicate becomes a next version of the
Quiz. All active Courses referring to the Quiz should be explicitely updated.

### Course management

One of Staff users creates a Course. This Staff user is the **Host** of the Course. Host schedules
the Exam: specifies a released Quiz and assigns the **Exam Period** - a period Students are
eligible to take the Exam within. Quiz and Exam Period are set on Course creation only. There
is a configurable settings **Preparation Interval** - time interval before Exam starts - which is
applied to to all Cources.

*Lifecycle* of a Course comprises the following *States*:

 * **Initial** - Host created the Course, specifying a Quiz, **Exam Period** - a period
Students are eligible to take the Exam within, **Trial Length** - time period a Student 
is allowed to *submit* her Trial within. Host is able to change Trial Length and include/exclude
Students in/from the Course till start of Exam Period minus Preparation Interval;
 * **Upcoming** - On the Preparation Interval start, the Course is disabled for any changes
and all Students included in the Course receive the **Exam Notification** of upcoming Exam;
 * **In Progress** - During Exam Period, every Student of the Course is able to take the Exam once;
 * **Ended** - On Exam Period end, the Course is done and any Trial submitions are rejected;
 * **Cancelled** - Host is able to cancel the Course before Exam Period. 
 
Student is notified of her inclusion/exclusion in/from the Course.

A Course may be duplicated in order to make another course keeping the Student list.

### Trial

Beginning from Exam Period start time, the Exam is available for Trials. A Student follows a
link provided by Exam Notification and starts her Trial. The Student submits every solution
during the Trial.  When the Student has done with the Quiz, she *finalize*s the Trial. If the
Student doesn't finalize the Trial during Trial Length, the Trial is finalized automatically
at Trial start time plus Trial Length.
