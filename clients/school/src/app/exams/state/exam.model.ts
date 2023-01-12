import { Person } from "../../persons.state"

export interface Exam {
  id: string
  quiz: QuizRef
  period: ExamPeriod
  host: Person
  state: string
  cacelledAt?: Date
  trialLength: number
  prestartAt: Date
}

export interface QuizRef {
  id: string
  title: string
}

export function createExam(params: Partial<Exam>) {
  return {
    ...params
  } as Exam
}
