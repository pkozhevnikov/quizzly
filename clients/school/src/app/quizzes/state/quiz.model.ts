export interface Quiz {
  id: string
  title: string
  obsolete: boolean
  inUse: boolean
  isPublished: boolean
  everPublished: boolean
  recommendedTrialLength: number
}

export function createQuiz(params: Partial<Quiz>) {
  return {
    id: "notset",
    title: "",
    obsolete: false,
    inUse: false,
    isPublished: false,
    everPublished: false,
    recommendedTrialLength: 0,
    ...params
  } as Quiz
}
