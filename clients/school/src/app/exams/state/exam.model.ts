import { Person } from "../../persons.state"

export interface Exam {
  id: string
  quiz: QuizRef
  period: ExamPeriod
  host: Person
  state: string
  cacelledAt?: Date
  trialLength: number
  passingGrade: number
  prestartAt: Date
}

export interface CreateExam {
  id: string
  quizId: string
  start: Date
  end: Date
  trialLength: number
  passingGrade: number
  testees: string[]
}

export interface QuizRef {
  id: string
  title: string
}

export interface ExamPeriod {
  start: Date
  end: Date
}

export function createExam(params: Partial<Exam>) {
  return {
    ...params
  } as Exam
}
