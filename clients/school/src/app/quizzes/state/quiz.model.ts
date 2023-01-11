export interface Quiz {
  id: string
  title: string
  obsolete: boolean
  inUse: boolean
  isPublished: boolean
  everPublished: boolean
}

export function createQuiz(params: Partial<Quiz>) {
  return {
    id: "notset",
    title: "",
    obsolete: false,
    inUse: false,
    isPublished: false,
    everPublished: false,
    ...params
  } as Quiz
}
