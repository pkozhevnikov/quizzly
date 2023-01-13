
export const matchers: jasmine.CustomMatcherFactories = {
  toHaveText: (util: jasmine.MatchersUtil) => {
    return {
      compare: function(actual: Element | null, expected: any) {
        const result = {pass: false, message: ""}
        if (actual instanceof HTMLElement) {
          result.pass = util.equals(actual.textContent, expected)
          result.message = result.pass ?
            `Expected element to contain text '${expected}'` :
            `Expected element to contain text '${expected}' but it was '${actual.textContent}'`
        } else {
          result.pass = false
          result.message = "Tested object should be a Element"
        }
        return result
      }
    }
  }
}

