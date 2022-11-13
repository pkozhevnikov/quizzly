## Quiz model

A Quiz is a container for questions. Questions with answers are presented by **Item**s.  Items are
grouped by **Section**s. Sections may be reordered within a Quiz, Items may be reordered within
a Section.

Generally, an Item is a question with a multi-choice answer, consists of:

 * *Intro* - question text
 * *Definition* - a *Statement* with text of question details and optional image 
 * *Hints* - an ordered set of variants of the answer, every variant is a set of alternative
 Statements, that consists of text and optional image
 * *Hints visible* flag - whether hints should be visible to Testees
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



