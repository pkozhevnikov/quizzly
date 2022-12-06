package author.dtos;


import rall.basic.swagger.enums.Status;

public class Results {

    public static class Result {
        public final boolean success;
        public final Status status;
        public final String description;

        public Result(boolean success, Status status, String description) {
            this.success = success;
            this.status = status;
            this.description = description;
        }
    }

    public static class listquizzes {
        public static final Result normalresponse = new Result(true, Status.OK, "normal response");
    }

    public static class createQuiz {
        public static final Result normalresponse = new Result(true, Status.OK, "normal response");
        public static final Result errorresponse = new Result(false, Status.EXPECTATION_FAILED, "error response");
    }

    public static class updatequiz {
        public static final Result normalresponse = new Result(true, Status.NO_CONTENT, "normal response");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class getquiz {
        public static final Result normalresponse = new Result(true, Status.OK, "normal response");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class addsection {
        public static final Result sectioncreatedreturnsidofnewsection = new Result(true, Status.OK, "section created, returns ID of new section");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class ownsection {
        public static final Result sectionowned = new Result(true, Status.NO_CONTENT, "section owned");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class setobsolete {
        public static final Result quizsetobsolete = new Result(true, Status.NO_CONTENT, "quiz set obsolete");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class setready {
        public static final Result signset = new Result(true, Status.NO_CONTENT, "sign set");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class unsetreadysign {
        public static final Result signunset = new Result(true, Status.NO_CONTENT, "sign unset");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class approvequiz {
        public static final Result approved = new Result(true, Status.NO_CONTENT, "approved");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class disapprove {
        public static final Result quizdisapproved = new Result(true, Status.NO_CONTENT, "quiz disapproved");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class updatesection {
        public static final Result sectionupdated = new Result(true, Status.NO_CONTENT, "section updated");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class movesection {
        public static final Result sectionmovedresponseisneworderofsectionids = new Result(true, Status.OK, "section moved, response is new order of section IDs");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class dischargesection {
        public static final Result sectiondischarged = new Result(true, Status.NO_CONTENT, "section discharged");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class removesection {
        public static final Result sectionremoved = new Result(true, Status.NO_CONTENT, "section removed");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class saveitem {
        public static final Result itemsaved = new Result(true, Status.NO_CONTENT, "item saved");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class additem {
        public static final Result itemaddedresponseswithidofnewitem = new Result(true, Status.OK, "item added, responses with ID of new item");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class moveitem {
        public static final Result itemmovedresponseswithneworderofitemids = new Result(true, Status.OK, "item moved, responses with new order of item IDs");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class removeitem {
        public static final Result itemremoved = new Result(true, Status.NO_CONTENT, "item removed");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class addauthor {
        public static final Result authoradded = new Result(true, Status.NO_CONTENT, "author added");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class removeauthor {
        public static final Result authorremoved = new Result(true, Status.NO_CONTENT, "author removed");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class addinspector {
        public static final Result inspectoradded = new Result(true, Status.NO_CONTENT, "inspector added");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class removeinspector {
        public static final Result inspectorremoved = new Result(true, Status.NO_CONTENT, "inspector removed");
        public static final Result error = new Result(false, Status.EXPECTATION_FAILED, "error");
    }

    public static class getpersonlist {
        public static final Result normalresponse = new Result(true, Status.OK, "normal response");
    }

}
