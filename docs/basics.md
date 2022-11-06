# Quizzly

A system to author, maintain, organize and perform testing for education.

## Basic terms

**Staff** - users that organize testing

**Student**s - users that are supposed to be tested

**Quiz** - a set of questions of a test

**Course** - a group of **Student**s that are supposed to take a specific **Quiz**

**Exam** - a session of taking a test 

## Core activities
  
All users are *registered* in the system through the **Registration** process.  **Staff** authors
**Quiz**zes. **Staff** forms **Course**s and schedules **Exam**s within **Course**s. **Student**s
take **Exam**s.

### Registration

**Registration** is initiated by **Invitation** made by any Staff user: a Staff user can invite
other user specifying **Purpose**(Staff or Student), email and optionally name of a person. The
invited person receives an email message with Invitation, follows a **Registration Link**,
specifies name (if required) and **Nickname**, and *confirms* the Registration. The user who
made the Invitation receives a notification of Registration, checks and *approves* or *declines*
Registration. Invited person receives a notification of resolution.  If the Registration is
approved, the notification contains a link to the **Set password page**.  The user specifies 
new password and gets access to the system.

Invitation is expired if Registration is not confirmed within configured timespan.

Invitation to the **First Staff user** is sent automatically to configured email during the
first run of the system.  Registration of the first Staff user is approved automatically.

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

 * **Composing** - Curator started the Quiz, Authors and Inspectors are notified about new Quiz
 start, the Quiz is available for changes by Authors
 * **Review** - All Authors report about rediness of the Quiz, Quiz gets read-only, Curator
 and Inspectors are notified about *readiness* of the Quiz, Inspectors *review* the Quiz and
 *approve* or *disapprove* the Quiz.  If the Quiz is disapproved, Quiz returns to the Composing
 state, Curator and Authors are notified about disapproval the Quiz.
 * **Released** - All Inspectors approve the Quiz, the Quiz gets read-only forever and is
 available for inclusion into Courses
 * **Deleted** - The Quiz becomes obsolete and doesn't correspond to the learning path anymore, Curator
 deletes the Quiz, Quiz is unavailable for inclusion into Courses

A Quiz is versioned: a Quiz may be duplicated, the duplicate becomes a next version of the
Quiz. All active Courses comprising the Quiz should be explicitely updated.

