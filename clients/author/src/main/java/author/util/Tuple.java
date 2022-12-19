package author.util;

public class Tuple {

  @lombok.Value
  public static class Tuple2<T1, T2> {
    T1 _1;
    T2 _2;
  }

  @lombok.Value
  public static class Tuple3<T1, T2, T3> {
    T1 _1;
    T2 _2;
    T3 _3;
  }

  public static <T1, T2> Tuple2<T1, T2> of(T1 _1, T2 _2) {
    return new Tuple2<>(_1, _2);
  }

  public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 _1, T2 _2, T3 _3) {
    return new Tuple3<>(_1, _2, _3);
  }

}
