## Quiz model

A Quiz is a container for questions. Questions with answers are presented by **Item**s.  Items are
grouped by **Section**s. Sections may be reordered within a Quiz, Items may be reordered within
a Section.

An Item is a question with a multi-choice answer which consists of:

 * *Intro* - question text
 * *Definition* - a *Statement* with text of question details and optional image 
 * *Hints* - an indexed list of variants of the answer, every variant is a set of alternative
 Statements, that consists of text and optional image
 * *Hints visible* flag whether hints should be visible to Testees
 * *Solutions* - list of indexes of correct hints

Hints may be reordered within an Item.

There's no explicit definition for types of Items. In fact, if Solutions has one element, the Item is
*sinlge-choice*. If Solutions has multiple elements, the Item is *multi-choice*.

Text of Item definition may contain placeholders, written as `{{hint_index}}`, which is a
link to a correct Hint. If Definition text has placeholders, the Item is *fill-blanks*. These
placeholders are shown to Testees as blanks, that are supposed to be filled by Testees with
their answers.


Examples:

#### Single-choice item
```
Intro: Is it correct?
Definition: 
  Statement: 2 + 3 = 7; no_image
Hints visible: yes
Hints:
  1.
    Statement: yes; no_image
  2.
    Statement: no; no_image
Solutions: 2
```

#### Multi-choice item
```
Intro: What's come?
Definition: 
  Statement: 4 + 3 = ?
Hints visible: yes
Hints:
  1.
    Statement: 5
  2.
    Statement: seven
  3.
    Statement: 10
  4.
    Statement: 7
  5.
    Statement: zero
Solutions: 2, 4
```

#### Fill-blanks item
```
Intro: Put the verb into the correct form.
Definition:
  Statement:
   1. Julie {{3}} (not / drink) tea very often.
   2. What time {{2}} (the banks / close) here?
   3. It {{1}} (take) me an hour to get to work. How long {{4}} (it / take) you?
Hints visible: no
Hints:
  1. 
    Statement: takes
  2. 
    Statement: do the banks close
  3. 
    Statement: does not drink 
    Statement: doesn't drink
  4. 
    Statement: does it take
Solutions: 3, 2, 1, 4
```


### The workflow

Curator:
 * creates a Quiz:
  * specifies general attributes;
  * specifies Authors and Inspectors;
 * modifies general attributes;
 * adds and removes Authors and Inspectors;
 * sets a Quiz Obsolete.

Author and Inspector group size should not be below than configured minimum.

Curator removes Authors and Inspectors. Author or Inspector cannot be removed if respective
group will be smaller than respecive minimum. When a Quiz is in Composing state, if an Author is
removed and she has already set her readiness sign, the readiness sign is also removed.  When a
Quiz is in Review state, if an Inspector is removed and she has already set her resolution,
the resolution is also removed.

Author adds a Section. To avoid conflicts of changes from different Authors, Sections are
modified exclusively by a single Author. Author *grab*s a Section thus becomes the owner of the
Section until the Author *discharge*s the Section. While an Author is the owner of the Section,
only this Author is able to add, modify, reorder and delete Items within the Section. If no
activity is registered within configured timespan, the section is discharged automatically,
thus is available to other Authors for modification. Author reorders Sections. Author removes
a Section if it is not owned by any Author at the moment.


Related links:

 * [User stories for Quiz authoring](../author/src/test/scala/accept/QuizAuthoringSpec.scala)
