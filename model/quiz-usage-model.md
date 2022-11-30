## Quiz usage model

All Released Quizzes are available for usage in Exams and Practicing. Releasing a Quiz entails
creation of **QuizFact** which controls availability of its related Quiz. Creation an Exam,
thus usage a particular Quiz in the Exam, is performed through consulting with related QuizFact.
Publishing a Quiz for Practicing is peformed by means of related QuizFact.

Not Obsolete and not published for Practicing Quizzes are listed on the *Exam List* and may be
used in Exams. The same Quiz may be used by multiple Exams.

All Quizzes that are used by neither Exams nor Practicing are listed on the *Idle List*,
visible to Officials and may be used in Exams or Practicing.

An Official publishes a Quiz for Practicing, a Quiz is moved from the Idle List to the *Practice
List*. Once a Quiz is published for Practicing, it cannot longer be used by Exams.  The Practice
List is visible to all, any Person is able to take a Practicing Quiz. An Official stops publishing
a Quiz for Practicing, the Quiz is moved to the Idle List but is not listed on the Exam List,
thus cannot be used by Exams anymore.

Curator of a Quiz marks the Quiz Obsolete. The Quiz is removed from Exam List and is listed on
the Idle List after all Exams that use the Quiz are Ended or Cancelled.
 
Related links:

 * [Exam model](../school/src/main/scala/Exam.scala)

