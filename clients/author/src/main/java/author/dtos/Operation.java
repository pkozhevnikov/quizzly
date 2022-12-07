package author.dtos;

import rall.basic.swagger.enums.Method;

public enum Operation {

    createQuiz("v1", "v1", "quiz", Method.POST, "Create new quiz"),
    listquizzes("v1", "v1", "quiz", Method.GET, "list quizzes"),
    addsection("v1", "v1", "quiz/{id}", Method.POST, "add section"),
    updatequiz("v1", "v1", "quiz/{id}", Method.PUT, "Update quiz"),
    setobsolete("v1", "v1", "quiz/{id}", Method.DELETE, "set quiz obsolete"),
    ownsection("v1", "v1", "quiz/{id}", Method.HEAD, "own section"),
    getquiz("v1", "v1", "quiz/{id}", Method.GET, "get quiz"),
    unsetreadysign("v1", "v1", "quiz/{id}/ready", Method.DELETE, "unset readiness sign"),
    setready("v1", "v1", "quiz/{id}/ready", Method.HEAD, "set readiness sign"),
    disapprove("v1", "v1", "quiz/{id}/resolve", Method.DELETE, "disapprove quiz"),
    approvequiz("v1", "v1", "quiz/{id}/resolve", Method.HEAD, "approve quiz"),
    movesection("v1", "v1", "section/{id}", Method.PATCH, "move section"),
    updatesection("v1", "v1", "section/{id}", Method.PUT, "update section"),
    removesection("v1", "v1", "section/{id}", Method.DELETE, "remove section"),
    dischargesection("v1", "v1", "section/{id}", Method.HEAD, "discharge section"),
    additem("v1", "v1", "section/{id}/items", Method.PATCH, "add item"),
    saveitem("v1", "v1", "section/{id}/items", Method.PUT, "save item"),
    moveitem("v1", "v1", "section/{id}/items/{itemId}", Method.PATCH, "move item"),
    removeitem("v1", "v1", "section/{id}/items/{itemId}", Method.DELETE, "remove item"),
    removeauthor("v1", "v1", "quiz/{id}/authors/{authorId}", Method.DELETE, "remove author"),
    addauthor("v1", "v1", "quiz/{id}/authors/{authorId}", Method.HEAD, "add author"),
    removeinspector("v1", "v1", "quiz/{id}/inspectors/{inspectorId}", Method.DELETE, "remove inspector"),
    addinspector("v1", "v1", "quiz/{id}/inspectors/{inspectorId}", Method.HEAD, "add inspector"),
    getpersonlist("v1", "v1", "staff", Method.GET, "get officials list"),
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
