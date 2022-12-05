package author.dtos;

import rall.basic.swagger.enums.Method;

public enum Operation {

    createQuiz("v1", "v1", "quiz", Method.POST, "Create new quiz"),
    listquizzes("v1", "v1", "quiz", Method.GET, "list quizzes"),
    updatequiz("v1", "v1", "quiz/{id}", Method.PUT, "Update quiz"),
    addsection("v1", "v1", "quiz/{id}", Method.POST, "add section"),
    ownsection("v1", "v1", "quiz/{id}", Method.HEAD, "own section"),
    getquiz("v1", "v1", "quiz/{id}", Method.GET, "get quiz"),
    setready("v1", "v1", "quiz/{id}/ready", Method.HEAD, "set readiness sign"),
    unsetreadysign("v1", "v1", "quiz/{id}/ready", Method.DELETE, "unset readiness sign"),
    approvequiz("v1", "v1", "quiz/{id}/resolve", Method.HEAD, "approve quiz"),
    disapprove("v1", "v1", "quiz/{id}/resolve", Method.DELETE, "disapprove quiz"),
    updatesection("v1", "v1", "section/{id}", Method.PUT, "update section"),
    dischargesection("v1", "v1", "section/{id}", Method.HEAD, "discharge section"),
    removesection("v1", "v1", "section/{id}", Method.DELETE, "remove section"),
    movesection("v1", "v1", "section/{id}", Method.PATCH, "move section"),
    saveitem("v1", "v1", "section/{id}/items", Method.PUT, "save item"),
    additem("v1", "v1", "section/{id}/items", Method.POST, "add item"),
    removeitem("v1", "v1", "section/{id}/items/{itemId}", Method.DELETE, "remove item"),
    moveitem("v1", "v1", "section/{id}/items/{itemId}", Method.PATCH, "move item"),
    ;

    protected Method method;
    protected String section;
    protected String path;
    protected String summary;
    protected String segment;

    Operation(String section, String segment, String path, Method method, String summary) {
        this.method = method;
        this.section = section;
        this.path = path;
        this.summary = summary;
        this.segment = segment;
    }

    public String getSegment() {
        return segment;
    }

    public String getSummary() {
        return summary;
    }

    public Method getMethod() {
        return method;
    }

    public String getSection() {
        return section;
    }

    public String getPath() {
        return path;
    }
}